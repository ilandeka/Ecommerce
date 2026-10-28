#!/usr/bin/env bash
# Google Cloud setup for the Ecommerce demo. Safe to run again: every step checks what already exists first.
#
#   ./bootstrap.sh            one-time setup: runtime identity, database, secrets, keyless GitHub access, Hosting site
#   ./bootstrap.sh stripe     store the Stripe TEST keys (secret key, webhook signing secret) in Secret Manager
#   ./bootstrap.sh schedule   after the first successful deploy: nightly reset of the demo data
#   ./bootstrap.sh mail       optional: store a Gmail app password of its own (the setup copies the booking demo's)
#
# The Ecommerce demo runs in the same Google Cloud project as the booking and Royale League demos and shares what the
# booking setup created: the Cloud SQL instance (this demo gets a database of its own), the container registry, the
# deployer service account, the keyless GitHub access and Firebase. Run the booking demo's bootstrap.sh first; this
# script stops with a clear message when something from it is missing. The public address is a Firebase Hosting site of
# its own that serves the Vue frontend; the frontend calls the Cloud Run API directly.
#
# Run it in Google Cloud Shell (https://shell.cloud.google.com), signed in as the owner of the project.
# Passwords are generated here and go straight into Secret Manager; nothing secret is printed.
#
# Settings (environment variables, all optional):
#   PROJECT_ID        Google Cloud project of the demos                   (default: ilandeka-demos)
#   REGION            region of Cloud Run, Cloud SQL and the registry     (default: europe-west1)
#   GITHUB_OWNER      GitHub user that owns the repositories              (default: ilandeka)
#   DEPLOY_REPOS      repositories allowed to deploy, space separated    (default: Ecommerce ecommerce-frontend)
#   ECOMMERCE_SITE_ID Firebase Hosting site, https://<name>.web.app       (default: ilandeka-ecommerce)

set -euo pipefail

PROJECT_ID="${PROJECT_ID:-ilandeka-demos}"
REGION="${REGION:-europe-west1}"
GITHUB_OWNER="${GITHUB_OWNER:-ilandeka}"
DEPLOY_REPOS="${DEPLOY_REPOS:-Ecommerce ecommerce-frontend}"
ECOMMERCE_SITE_ID="${ECOMMERCE_SITE_ID:-ilandeka-ecommerce}"

# Created by the booking demo's bootstrap.sh and shared.
SQL_INSTANCE="demo-pg"
REGISTRY_REPO="demo"
DEPLOYER_SA_ID="gh-deployer"
SCHEDULER_SA_ID="demo-scheduler"
WIF_POOL="github"
BOOKING_SECRET_MAIL_PASSWORD="booking-mail-password"

# The Ecommerce demo's own. These names are also used by .github/workflows/ci-cd.yml: change them only together with it.
DB_NAME="ecommerce"
DB_USER="ecommerce"
RUNTIME_SA_ID="ecommerce-api"
SECRET_DB_PASSWORD="ecommerce-db-password"
SECRET_JWT="ecommerce-jwt-secret"
SECRET_ADMIN_CODE="ecommerce-admin-code"
SECRET_MAIL_PASSWORD="ecommerce-mail-password"
SECRET_STRIPE_KEY="ecommerce-stripe-secret-key"
SECRET_STRIPE_WEBHOOK="ecommerce-stripe-webhook-secret"
CLOUD_RUN_SERVICE="ecommerce-api"
CLOUD_RUN_RESET_JOB="ecommerce-demo-reset"

PROJECT_NUMBER=""
ACCOUNT=""
WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

# gcloud never stops to ask a question; every gcloud call below targets the project once it is selected.
export CLOUDSDK_CORE_DISABLE_PROMPTS=1

# ---------------------------------------------------------------------------------------------------------------
# Small helpers
# ---------------------------------------------------------------------------------------------------------------

say()  { printf '\n==> %s\n' "$*"; }
note() { printf '    %s\n' "$*"; }
warn() { printf '    WARNING: %s\n' "$*" >&2; }
die()  { printf '\nERROR: %s\n' "$*" >&2; exit 1; }

# Prints the comment block at the top of this file (from line 2 to the first empty line).
usage() {
  sed -n '2,/^$/p' "$0" | sed -e '/^$/d' -e 's/^# \{0,1\}//'
}

# retry <attempts> <seconds between attempts> <command...>
# New service accounts and permissions take a little while to become visible everywhere.
retry() {
  local attempts="$1" delay="$2" n=1
  shift 2
  until "$@"; do
    if [ "$n" -ge "$attempts" ]; then
      return 1
    fi
    n=$((n + 1))
    sleep "$delay"
  done
}

sa_email() { printf '%s@%s.iam.gserviceaccount.com' "$1" "$PROJECT_ID"; }

require_tools() {
  local tool
  for tool in gcloud openssl curl; do
    command -v "$tool" >/dev/null 2>&1 || die "'${tool}' is needed but was not found. Use Google Cloud Shell, where it is installed."
  done
  ACCOUNT="$(gcloud config get-value account 2>/dev/null || true)"
  if [ -z "$ACCOUNT" ] || [ "$ACCOUNT" = "(unset)" ]; then
    die "You are not signed in to Google Cloud. Run: gcloud auth login"
  fi
}

use_existing_project() {
  gcloud projects describe "$PROJECT_ID" >/dev/null 2>&1 \
    || die "Project ${PROJECT_ID} was not found. Run the booking demo's bootstrap.sh first (or set PROJECT_ID to your project)."
  PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
  export CLOUDSDK_CORE_PROJECT="$PROJECT_ID"
}

ensure_service_account() { # <id> <display name>
  if gcloud iam service-accounts describe "$(sa_email "$1")" >/dev/null 2>&1; then
    return 0
  fi
  gcloud iam service-accounts create "$1" --display-name="$2" >/dev/null
}

grant_project_role() { # <service account email> <role>
  retry 8 6 gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:$1" --role="$2" --condition=None >/dev/null
}

secret_exists() { gcloud secrets describe "$1" >/dev/null 2>&1; }

# put_secret <name> <value>: create the secret or add a new version. The value goes through a pipe, never
# onto a command line, and is never printed.
put_secret() {
  if secret_exists "$1"; then
    printf '%s' "$2" | gcloud secrets versions add "$1" --data-file=- >/dev/null
  else
    printf '%s' "$2" | gcloud secrets create "$1" \
      --replication-policy=user-managed --locations="$REGION" --data-file=- >/dev/null
  fi
}

allow_secret_access() { # <secret name>
  retry 8 6 gcloud secrets add-iam-policy-binding "$1" \
    --member="serviceAccount:$(sa_email "$RUNTIME_SA_ID")" --role=roles/secretmanager.secretAccessor >/dev/null
}

# google_api <METHOD> <URL> [JSON body]: calls a Google REST API as you and prints the HTTP status code.
# The response body is kept in $WORKDIR/response.json. (Firebase has no gcloud command for these steps.)
google_api() {
  local method="$1" url="$2" body="${3:-}" token
  token="$(gcloud auth print-access-token)"
  if [ -n "$body" ]; then
    curl -sS -o "$WORKDIR/response.json" -w '%{http_code}' -X "$method" \
      -H "Authorization: Bearer ${token}" -H "X-Goog-User-Project: ${PROJECT_ID}" \
      -H "Content-Type: application/json" -d "$body" "$url" || true
  else
    curl -sS -o "$WORKDIR/response.json" -w '%{http_code}' -X "$method" \
      -H "Authorization: Bearer ${token}" -H "X-Goog-User-Project: ${PROJECT_ID}" "$url" || true
  fi
}

# ---------------------------------------------------------------------------------------------------------------
# Setup steps
# ---------------------------------------------------------------------------------------------------------------

check_shared_setup() {
  say "What the booking demo's setup provides"
  gcloud sql instances describe "$SQL_INSTANCE" >/dev/null 2>&1 \
    || die "The Cloud SQL instance ${SQL_INSTANCE} does not exist. Run the booking demo's bootstrap.sh first."
  note "Cloud SQL instance ${SQL_INSTANCE}"
  gcloud artifacts repositories describe "$REGISTRY_REPO" --location="$REGION" >/dev/null 2>&1 \
    || die "The container registry ${REGISTRY_REPO} does not exist. Run the booking demo's bootstrap.sh first."
  note "container registry ${REGISTRY_REPO}"
  gcloud iam service-accounts describe "$(sa_email "$DEPLOYER_SA_ID")" >/dev/null 2>&1 \
    || die "The service account ${DEPLOYER_SA_ID} does not exist. Run the booking demo's bootstrap.sh first."
  note "deployer service account ${DEPLOYER_SA_ID}"
  gcloud iam workload-identity-pools describe "$WIF_POOL" --location=global >/dev/null 2>&1 \
    || die "The Workload Identity pool ${WIF_POOL} does not exist. Run the booking demo's bootstrap.sh first."
  note "keyless GitHub access (Workload Identity pool ${WIF_POOL})"
}

ensure_identities() {
  say "Runtime identity and permissions"
  local deployer runtime
  deployer="$(sa_email "$DEPLOYER_SA_ID")"
  runtime="$(sa_email "$RUNTIME_SA_ID")"

  ensure_service_account "$RUNTIME_SA_ID" "Ecommerce API runtime"

  # The runtime account is what the running service uses: it may open the Cloud SQL connection and nothing else.
  # (Access to each secret is granted below, one secret at a time.)
  grant_project_role "$runtime" roles/cloudsql.client
  note "${RUNTIME_SA_ID}: may connect to Cloud SQL"

  # The deployer (used by GitHub Actions) may run Cloud Run revisions as the runtime account.
  retry 8 6 gcloud iam service-accounts add-iam-policy-binding "$runtime" \
    --member="serviceAccount:${deployer}" --role=roles/iam.serviceAccountUser >/dev/null
  note "${DEPLOYER_SA_ID}: may deploy services that run as ${RUNTIME_SA_ID}"
}

ensure_github_access() {
  say "Keyless access for GitHub Actions"
  local deployer member repo
  local -a repos
  deployer="$(sa_email "$DEPLOYER_SA_ID")"
  # The pool's provider already accepts your repositories on main or master; only the repositories themselves have to
  # be allowed to act as the deployer. The backend deploys Cloud Run, the frontend deploys to Firebase Hosting.
  IFS=' ' read -r -a repos <<<"$DEPLOY_REPOS"
  for repo in "${repos[@]}"; do
    member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${WIF_POOL}/attribute.repository/${GITHUB_OWNER}/${repo}"
    retry 8 6 gcloud iam service-accounts add-iam-policy-binding "$deployer" \
      --member="$member" --role=roles/iam.workloadIdentityUser >/dev/null
    note "${GITHUB_OWNER}/${repo} may deploy (from main or master)"
  done
}

ensure_firebase_site() {
  say "Firebase Hosting site (the public address of the frontend)"
  local status

  # Firebase was added to the project by the booking demo's setup (it needs one click in the browser, once).
  status="$(google_api GET "https://firebase.googleapis.com/v1beta1/projects/${PROJECT_ID}")"
  [ "$status" = "200" ] \
    || die "Firebase is not enabled on ${PROJECT_ID} yet. Run the booking demo's bootstrap.sh first; it guides you through the one click."

  status="$(google_api GET "https://firebasehosting.googleapis.com/v1beta1/projects/${PROJECT_ID}/sites/${ECOMMERCE_SITE_ID}")"
  if [ "$status" = "200" ]; then
    note "site ${ECOMMERCE_SITE_ID} already exists"
    return 0
  fi
  status="$(google_api POST "https://firebasehosting.googleapis.com/v1beta1/projects/${PROJECT_ID}/sites?siteId=${ECOMMERCE_SITE_ID}" '{}')"
  if [ "$status" = "200" ]; then
    note "created https://${ECOMMERCE_SITE_ID}.web.app"
  else
    cat "$WORKDIR/response.json" >&2 || true
    die "Could not create the Hosting site '${ECOMMERCE_SITE_ID}' (HTTP ${status}). Site names are global; if it is taken, choose another: ECOMMERCE_SITE_ID=<name> ./bootstrap.sh (and set it in deploy/gcp/config.env of both repositories too)."
  fi
}

ensure_database_and_secrets() {
  say "Database, user and secrets"
  local found db_password mail_password

  found="$(gcloud sql databases list --instance="$SQL_INSTANCE" --filter="name=${DB_NAME}" --format='value(name)')"
  if [ -z "$found" ]; then
    gcloud sql databases create "$DB_NAME" --instance="$SQL_INSTANCE" >/dev/null
    note "database ${DB_NAME} created on ${SQL_INSTANCE}"
  else
    note "database ${DB_NAME} already exists"
  fi

  # The database password is generated once and kept in Secret Manager; running the script again reuses it.
  if secret_exists "$SECRET_DB_PASSWORD"; then
    db_password="$(gcloud secrets versions access latest --secret="$SECRET_DB_PASSWORD")"
  else
    db_password="$(openssl rand -hex 24)"
    put_secret "$SECRET_DB_PASSWORD" "$db_password"
    note "secret ${SECRET_DB_PASSWORD} created"
  fi
  found="$(gcloud sql users list --instance="$SQL_INSTANCE" --filter="name=${DB_USER}" --format='value(name)')"
  if [ -z "$found" ]; then
    gcloud sql users create "$DB_USER" --instance="$SQL_INSTANCE" --password="$db_password" >/dev/null
    note "database user ${DB_USER} created"
  else
    gcloud sql users set-password "$DB_USER" --instance="$SQL_INSTANCE" --password="$db_password" >/dev/null
    note "database user ${DB_USER} already exists (password synced with Secret Manager)"
  fi
  unset db_password
  allow_secret_access "$SECRET_DB_PASSWORD"

  # The key that signs the login tokens (JWT) is generated once as well. 48 random bytes are 64 characters.
  if secret_exists "$SECRET_JWT"; then
    note "secret ${SECRET_JWT} already exists"
  else
    put_secret "$SECRET_JWT" "$(openssl rand -base64 48 | tr -d '\n')"
    note "secret ${SECRET_JWT} created"
  fi
  allow_secret_access "$SECRET_JWT"

  # The code that allows registering an admin account (POST /api/auth/register/admin). Only you need it; read it in
  # the Secret Manager console when you want to create another admin.
  if secret_exists "$SECRET_ADMIN_CODE"; then
    note "secret ${SECRET_ADMIN_CODE} already exists"
  else
    put_secret "$SECRET_ADMIN_CODE" "$(openssl rand -hex 16)"
    note "secret ${SECRET_ADMIN_CODE} created"
  fi
  allow_secret_access "$SECRET_ADMIN_CODE"

  # The demo sends its email from the same Gmail account as the booking demo. Copy that app password instead of
  # asking for it again; it is never printed.
  if secret_exists "$SECRET_MAIL_PASSWORD"; then
    note "secret ${SECRET_MAIL_PASSWORD} already exists"
  elif secret_exists "$BOOKING_SECRET_MAIL_PASSWORD"; then
    mail_password="$(gcloud secrets versions access latest --secret="$BOOKING_SECRET_MAIL_PASSWORD")"
    put_secret "$SECRET_MAIL_PASSWORD" "$mail_password"
    unset mail_password
    note "secret ${SECRET_MAIL_PASSWORD} created from ${BOOKING_SECRET_MAIL_PASSWORD}"
  else
    warn "No Gmail app password stored yet. Run ./bootstrap.sh mail, or set MAIL_ACCOUNT_ENABLED=false in deploy/gcp/config.env."
  fi
  if secret_exists "$SECRET_MAIL_PASSWORD"; then
    allow_secret_access "$SECRET_MAIL_PASSWORD"
  fi

  for secret in "$SECRET_STRIPE_KEY" "$SECRET_STRIPE_WEBHOOK"; do
    if secret_exists "$secret"; then
      allow_secret_access "$secret"
    fi
  done
}

print_summary() {
  say "Done. Everything is ready for the first deploy."
  cat <<EOF

    deploy/gcp/config.env already has these identifiers (they are not secrets):

        GCP_PROJECT_ID=${PROJECT_ID}
        GCP_PROJECT_NUMBER=${PROJECT_NUMBER}
        GCP_REGION=${REGION}
        ECOMMERCE_SITE_ID=${ECOMMERCE_SITE_ID}

    Next:
      1. ./bootstrap.sh stripe     (your Stripe TEST keys; then STRIPE_ENABLED=true in config.env)
      2. merge the backend pull request to master, then the frontend one to master
      3. ./bootstrap.sh schedule   (after the first successful backend deploy)

    The addresses are:
        Shop (Hosting)  https://${ECOMMERCE_SITE_ID}.web.app
        API (Cloud Run) https://${CLOUD_RUN_SERVICE}-${PROJECT_NUMBER}.${REGION}.run.app
        Stripe webhook  https://${CLOUD_RUN_SERVICE}-${PROJECT_NUMBER}.${REGION}.run.app/api/payments/webhook
EOF
}

cmd_setup() {
  require_tools
  say "Signed in as ${ACCOUNT}"
  use_existing_project
  note "project ${PROJECT_ID} (number ${PROJECT_NUMBER})"
  check_shared_setup
  ensure_identities
  ensure_github_access
  ensure_firebase_site
  ensure_database_and_secrets
  print_summary
}

# ---------------------------------------------------------------------------------------------------------------
# Stripe test keys
# ---------------------------------------------------------------------------------------------------------------

cmd_stripe() {
  require_tools
  use_existing_project
  say "Stripe TEST keys (what you type is hidden and goes straight into Secret Manager)"
  note "Use test mode only: the demo takes test cards, never real payments."
  note "Secret key:  Stripe Dashboard (test mode), Developers, API keys, 'Secret key' (starts with sk_test_)."
  note "Webhook:     Stripe Dashboard (test mode), Developers, Webhooks, Add endpoint. The address is known before the"
  note "             first deploy, so you can do this first:"
  note "               URL     https://${CLOUD_RUN_SERVICE}-${PROJECT_NUMBER}.${REGION}.run.app/api/payments/webhook"
  note "               events  payment_intent.succeeded, payment_intent.payment_failed, charge.refunded"
  note "             then copy its 'Signing secret' (starts with whsec_). If you do not have it yet, press Enter and"
  note "             run this command again later."
  local key webhook
  ensure_service_account "$RUNTIME_SA_ID" "Ecommerce API runtime"

  read -r -s -p "    Stripe secret key (sk_test_..., Enter keeps the stored one): " key
  printf '\n'
  key="${key// /}"
  if [ -n "$key" ]; then
    case "$key" in
      sk_live_*|rk_live_*) unset key; die "That is a LIVE key. The demo only takes test keys (sk_test_...); nothing was stored." ;;
      sk_test_*|rk_test_*) ;;
      *) warn "That does not look like a Stripe test key (sk_test_...). It is stored anyway; run this command again to replace it." ;;
    esac
    put_secret "$SECRET_STRIPE_KEY" "$key"
    note "secret ${SECRET_STRIPE_KEY} stored"
  elif secret_exists "$SECRET_STRIPE_KEY"; then
    note "secret ${SECRET_STRIPE_KEY} kept"
  else
    die "The Stripe secret key is required."
  fi
  unset key
  allow_secret_access "$SECRET_STRIPE_KEY"

  read -r -s -p "    Webhook signing secret (whsec_..., Enter to skip or keep): " webhook
  printf '\n'
  webhook="${webhook// /}"
  if [ -n "$webhook" ]; then
    case "$webhook" in
      whsec_*) ;;
      *) warn "That does not look like a Stripe signing secret (whsec_...). It is stored anyway; run this command again to replace it." ;;
    esac
    put_secret "$SECRET_STRIPE_WEBHOOK" "$webhook"
    note "secret ${SECRET_STRIPE_WEBHOOK} stored"
  elif secret_exists "$SECRET_STRIPE_WEBHOOK"; then
    note "secret ${SECRET_STRIPE_WEBHOOK} kept"
  else
    # The deploy maps both secrets, so the second one has to exist. Until the real one is stored, every webhook fails
    # its signature check: checkout works, but orders stay unpaid.
    put_secret "$SECRET_STRIPE_WEBHOOK" "whsec_not_set_yet"
    warn "No webhook secret yet: a placeholder was stored, so paid orders will not be marked as paid until you run ./bootstrap.sh stripe again with the real one."
  fi
  unset webhook
  allow_secret_access "$SECRET_STRIPE_WEBHOOK"

  cat <<EOF

    Stored. Set STRIPE_ENABLED=true in deploy/gcp/config.env (and the publishable key in the frontend's config.env).
    Cloud Run reads the secrets when an instance starts, so re-run the CI/CD workflow to use new values.
EOF
}

# ---------------------------------------------------------------------------------------------------------------
# After the first deploy: nightly reset of the demo data
# ---------------------------------------------------------------------------------------------------------------

# upsert_scheduler_job <name> <cron> <uri> <method> <description> [extra flags...]
upsert_scheduler_job() {
  local name="$1" cron="$2" uri="$3" method="$4" description="$5" verb
  shift 5
  if gcloud scheduler jobs describe "$name" --location="$REGION" >/dev/null 2>&1; then
    verb="update"
  else
    verb="create"
  fi
  gcloud scheduler jobs "$verb" http "$name" --location="$REGION" \
    --schedule="$cron" --time-zone="Europe/Zagreb" --uri="$uri" --http-method="$method" \
    --description="$description" "$@" >/dev/null
  note "${verb}d scheduler job ${name} (${cron}, Europe/Zagreb)"
}

cmd_schedule() {
  require_tools
  use_existing_project
  say "Scheduled jobs"
  local scheduler
  gcloud run services describe "$CLOUD_RUN_SERVICE" --region="$REGION" >/dev/null 2>&1 \
    || die "The ${CLOUD_RUN_SERVICE} service does not exist yet. Deploy first (merge to master), then run this again."
  gcloud run jobs describe "$CLOUD_RUN_RESET_JOB" --region="$REGION" >/dev/null 2>&1 \
    || die "The ${CLOUD_RUN_RESET_JOB} job does not exist yet. It is created by the same deploy as the service."

  # The booking demo's schedule step creates this account too; reuse it when it is there.
  ensure_service_account "$SCHEDULER_SA_ID" "Cloud Scheduler caller"
  scheduler="$(sa_email "$SCHEDULER_SA_ID")"
  retry 8 6 gcloud run jobs add-iam-policy-binding "$CLOUD_RUN_RESET_JOB" --region="$REGION" \
    --member="serviceAccount:${scheduler}" --role=roles/run.invoker >/dev/null

  # 03:30 Zagreb time (booking resets at 03:00, Royale League at 03:15): wipe whatever visitors created and bring the
  # demo data back
  upsert_scheduler_job "ecommerce-demo-reset" "30 3 * * *" \
    "https://run.googleapis.com/v2/projects/${PROJECT_ID}/locations/${REGION}/jobs/${CLOUD_RUN_RESET_JOB}:run" POST \
    "Nightly reset of the Ecommerce demo data" --oauth-service-account-email="$scheduler"

  cat <<EOF

    Run the reset right now to check it works:
        gcloud scheduler jobs run ecommerce-demo-reset --location=${REGION}
    and look at the result under Cloud Run > Jobs > ${CLOUD_RUN_RESET_JOB} > Executions.
EOF
}

# ---------------------------------------------------------------------------------------------------------------
# Optional: a Gmail app password of its own
# ---------------------------------------------------------------------------------------------------------------

cmd_mail() {
  require_tools
  use_existing_project
  say "Gmail app password (what you type is hidden and goes straight into Secret Manager)"
  note "Use an app password of the Gmail account in MAIL_USERNAME (deploy/gcp/config.env), not its normal password."
  note "Create one at https://myaccount.google.com/apppasswords (the account needs 2-Step Verification)."
  local secret
  read -r -s -p "    Gmail app password: " secret
  printf '\n'
  # Google shows app passwords as four groups of four letters; the spaces are not part of the password.
  secret="${secret// /}"
  if [ -z "$secret" ]; then
    die "The app password is required."
  fi
  if ! [[ "$secret" =~ ^[a-z]{16}$ ]]; then
    warn "That does not look like a Gmail app password (16 letters). It is stored anyway; run this command again to replace it."
  fi
  ensure_service_account "$RUNTIME_SA_ID" "Ecommerce API runtime"
  put_secret "$SECRET_MAIL_PASSWORD" "$secret"
  unset secret
  allow_secret_access "$SECRET_MAIL_PASSWORD"
  cat <<EOF

    Stored. The address is not a secret: it is MAIL_USERNAME in deploy/gcp/config.env.
    Cloud Run reads the secret when an instance starts, so re-run the CI/CD workflow to use a new password.
EOF
}

# ---------------------------------------------------------------------------------------------------------------

main() {
  case "${1:-setup}" in
    setup)    cmd_setup ;;
    stripe)   cmd_stripe ;;
    schedule) cmd_schedule ;;
    mail)     cmd_mail ;;
    -h|--help|help) usage ;;
    *) usage; exit 2 ;;
  esac
}

main "$@"
