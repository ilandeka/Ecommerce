package com.example.ecommerce.config;

import com.example.ecommerce.model.entity.Product;
import com.example.ecommerce.model.entity.Role;
import com.example.ecommerce.model.entity.User;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.service.ImageService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

@Component
public class DataLoader implements CommandLineRunner {
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;
    private final ImageService imageService;

    public DataLoader(UserRepository userRepository,
                      ProductRepository productRepository,
                      PasswordEncoder passwordEncoder, ImageService imageService) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
        this.imageService = imageService;
    }

    @Override
    public void run(String... args) {
        // First, let's check if we already have users
        // This prevents creating duplicate data every time the app starts
        if (userRepository.count() == 0) {
            createUsers();
        }

        // Similarly, check if we have products
        if (productRepository.count() == 0) {
            createProducts();
        }
    }

    private void createUsers() {
        // Create admin user
        User admin = new User();
        admin.setEmail("admin@admin.com");
        admin.setPassword(passwordEncoder.encode("test"));
        admin.setFullName("Admin User");
        admin.setEnabled(true);
        admin.setRoles(new HashSet<>(Arrays.asList(Role.ROLE_ADMIN, Role.ROLE_USER)));
        userRepository.save(admin);

        // Create regular test user
        User user = new User();
        user.setEmail("test@test.com");
        user.setPassword(passwordEncoder.encode("test"));
        user.setFullName("Test User");
        user.setEnabled(true);
        user.setRoles(new HashSet<>(List.of(Role.ROLE_USER)));
        userRepository.save(user);
    }

    private void createProducts() {
        // Create some sample products
        Product laptop = new Product();
        laptop.setName("Gaming Laptop");
        laptop.setDescription("High-performance gaming laptop with RTX 3080");
        laptop.setPrice(new BigDecimal("1299.99"));
        laptop.setStockQuantity(10);
        laptop.setAvailable(true);
        laptop.setImageUrl(imageService.getDefaultImageUrl());
        productRepository.save(laptop);

        Product smartphone = new Product();
        smartphone.setName("Smartphone X");
        smartphone.setDescription("Latest model with 5G capability");
        smartphone.setPrice(new BigDecimal("899.99"));
        smartphone.setStockQuantity(15);
        smartphone.setAvailable(true);
        smartphone.setImageUrl(imageService.getDefaultImageUrl());
        productRepository.save(smartphone);

        Product headphones = new Product();
        headphones.setName("Wireless Headphones");
        headphones.setDescription("Noise-cancelling Bluetooth headphones");
        headphones.setPrice(new BigDecimal("199.99"));
        headphones.setStockQuantity(20);
        headphones.setAvailable(true);
        headphones.setImageUrl(imageService.getDefaultImageUrl());
        productRepository.save(headphones);

        Product software = new Product();
        software.setName("Software X");
        software.setDescription("Operating System X");
        software.setPrice(new BigDecimal("99.99"));
        software.setStockQuantity(30);
        software.setAvailable(true);
        software.setImageUrl(imageService.getDefaultImageUrl());
        productRepository.save(software);
    }
}