package com.eshop.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class ImageService {
    private final String uploadDir;

    @Value("${app.image.default-product}")
    private String defaultProductImage;

    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg",
            "image/png",
            "image/gif"
    );

    private static final long MAX_FILE_SIZE = 5_000_000; // 5MB

    public ImageService(@Value("${app.image.storage.location}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public String saveProductImage(MultipartFile file, Long productId) {
        if (file == null || file.isEmpty()) {
            log.info("No image provided for product {}. Using default.", productId);
            return defaultProductImage;
        }

        validateImage(file);

        try {
            String filename = generateFilename(file, productId);
            Path targetLocation = Paths.get(uploadDir).resolve(filename);

            // Copy file to target location (replace if exists)
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            log.info("Saved image for product {}: {}", productId, filename);

            // Return the URL path that will be used to serve the image
            return "/uploads/products/" + filename;

        } catch (IOException ex) {
            throw new RuntimeException("Could not store file", ex);
        }
    }

    private void validateImage(MultipartFile file) {
        // Check file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("File size exceeds maximum limit");
        }

        // Check file type
        String contentType = file.getContentType();
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new RuntimeException("File type not supported");
        }

        // Additional validation could be added here
        // e.g., check image dimensions, scan for malware, etc.
    }

    private String generateFilename(MultipartFile file, Long productId) {
        // Extract file extension from original filename
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // Generate unique filename using product ID and timestamp
        return String.format("product_%d_%d%s",
                productId,
                System.currentTimeMillis(),
                extension);
    }

    public void deleteProductImage(String imageUrl) {
        if (imageUrl.equals(defaultProductImage)) {
            return; // Don't delete the default image
        }

        try {
            // Extract filename from URL
            String filename = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
            Path filePath = Paths.get(uploadDir).resolve(filename);

            Files.deleteIfExists(filePath);
            log.info("Deleted image: {}", filename);
        } catch (IOException ex) {
            log.error("Error deleting image: {}", imageUrl, ex);
        }
    }

    public boolean isDefaultImage(String imageUrl) {
        return defaultProductImage.equals(imageUrl);
    }

    public String getDefaultImageUrl() {
        return defaultProductImage;
    }
}