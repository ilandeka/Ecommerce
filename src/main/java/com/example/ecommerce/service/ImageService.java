package com.example.ecommerce.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@Slf4j
public class ImageService {
    private static final String DEFAULT_IMAGE_PATH = "/images/default-product.jpg";
    private static final String UPLOAD_DIR = "uploads/products/";
    private static final long MAX_FILE_SIZE = 5_000_000; // 5MB

    @PostConstruct
    public void init() {
        try {
            // Create the uploads directory when the application starts
            Files.createDirectories(Path.of(UPLOAD_DIR));
            log.info("Product image upload directory initialized at: {}", UPLOAD_DIR);
        } catch (IOException e) {
            log.error("Could not create upload directory", e);
            throw new RuntimeException("Could not create upload directory!", e);
        }
    }

    public String saveProductImage(MultipartFile file, Long productId) {
        // If no file is provided, return the default image path
        if (file == null || file.isEmpty()) {
            log.info("No image provided for product {}. Using default image.", productId);
            return DEFAULT_IMAGE_PATH;
        }

        try {
            // Validate the image file
            validateImageFile(file);

            // Create a unique filename using product ID and timestamp
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null ?
                    originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
            String filename = String.format("product_%d_%d%s",
                    productId, System.currentTimeMillis(), extension);

            // Create the full path and save the file
            Path filePath = Path.of(UPLOAD_DIR + filename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            log.info("Saved image for product {}: {}", productId, filename);
            return "/uploads/products/" + filename;

        } catch (IOException e) {
            log.error("Failed to store image file for product {}", productId, e);
            throw new RuntimeException("Failed to store image file", e);
        }
    }

    private void validateImageFile(MultipartFile file) {
        // Check file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("File size must be less than 5MB");
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("File must be an image");
        }
    }

    public String getDefaultImageUrl() {
        return DEFAULT_IMAGE_PATH;
    }
}