package com.eshop.service;

import com.eshop.model.dto.AddToCartRequest;
import com.eshop.model.dto.CartResponse;
import com.eshop.model.entity.Cart;
import com.eshop.model.entity.CartItem;
import com.eshop.model.entity.Product;
import com.eshop.repository.CartRepository;
import com.eshop.repository.ProductRepository;
import com.eshop.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartService(CartRepository cartRepository, ProductRepository productRepository, UserRepository userRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    public CartResponse getCartResponse(Long userId) {
        Cart cart = getOrCreateCart(userId);
        return createCartResponse(cart);
    }

    public CartResponse updateCartItem(Long userId, AddToCartRequest request) {
        Cart cart = getOrCreateCart(userId);
        CartItem cartItem = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(request.getProductId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Item not in cart"));

        cartItem.setQuantity(request.getQuantity());
        updateCartTotal(cart);
        cartRepository.save(cart);

        return createCartResponse(cart);
    }

    public CartResponse removeFromCart(Long userId, Long productId) {
        Cart cart = getOrCreateCart(userId);
        cart.getItems().removeIf(item -> item.getProduct().getId().equals(productId));
        updateCartTotal(cart);
        cartRepository.save(cart);

        return createCartResponse(cart);
    }

    public CartResponse addToCart(Long userId, AddToCartRequest request) {
        Cart cart = getOrCreateCart(userId);
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (product.getStockQuantity() < request.getQuantity()) {
            throw new RuntimeException("Not enough stock");
        }

        CartItem cartItem = findOrCreateCartItem(cart, product);
        cartItem.setQuantity(cartItem.getQuantity() + request.getQuantity());
        cartItem.setPrice(product.getPrice());

        updateCartTotal(cart);
        cartRepository.save(cart);

        return createCartResponse(cart);
    }

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Cart newCart = new Cart();
                    newCart.setUser(userRepository.getReferenceById(userId));
                    return cartRepository.save(newCart);
                });
    }

    private CartItem findOrCreateCartItem(Cart cart, Product product) {
        return cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst()
                .orElseGet(() -> {
                    CartItem newItem = new CartItem();
                    newItem.setCart(cart);
                    newItem.setProduct(product);
                    newItem.setQuantity(0);
                    cart.getItems().add(newItem);
                    return newItem;
                });
    }

    private void updateCartTotal(Cart cart) {
        cart.setTotal(cart.getItems().stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public Cart getCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Cart not found"));
    }

    @Transactional
    public void clearCart(Long userId) {
        Cart cart = getCart(userId);
        cart.getItems().clear();
        cart.setTotal(BigDecimal.ZERO);
        cartRepository.save(cart);
    }

    private CartResponse createCartResponse(Cart cart) {
        CartResponse response = new CartResponse();
        response.setTotal(cart.getTotal());

        List<CartResponse.CartItemDTO> items = cart.getItems().stream()
                .map(item -> {
                    CartResponse.CartItemDTO dto = new CartResponse.CartItemDTO();
                    dto.setProductId(item.getProduct().getId());
                    dto.setProductName(item.getProduct().getName());
                    dto.setQuantity(item.getQuantity());
                    dto.setPrice(item.getPrice());
                    dto.setSubtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                    return dto;
                })
                .collect(Collectors.toList());

        response.setItems(items);
        return response;
    }

}