package com.example.ecommerce.service;

import com.example.ecommerce.model.dto.ProductRequest;
import com.example.ecommerce.model.dto.ProductResponse;
import com.example.ecommerce.model.entity.Product;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.util.SortingUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class ProductService {
    private final ProductRepository productRepository;
    private final ImageService imageService;

    public ProductService(ProductRepository productRepository, ImageService imageService) {
        this.productRepository = productRepository;
        this.imageService = imageService;
    }

    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        // Validate that sort fields are allowed
        if (pageable.getSort().isSorted()) {
            pageable.getSort().forEach(order -> {
                String property = order.getProperty();
                if (!SortingUtils.ALLOWED_PRODUCT_FIELDS.contains(property)) {
                    throw new IllegalArgumentException(
                            "Invalid sort field: " + property +
                                    ". Allowed fields are: " +
                                    SortingUtils.ALLOWED_PRODUCT_FIELDS
                    );
                }
            });
        }

        // Fetch products with sorting
        return productRepository.findAll(pageable)
                .map(this::mapToResponse);
    }

    public ProductResponse getProduct(Long id) {
        return productRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    public ProductResponse createProduct(ProductRequest request, MultipartFile image) {
        Product product = new Product();
        updateProductFromRequest(product, request);

        // Save the product first to get its ID
        product = productRepository.save(product);

        // Handle image upload if provided, otherwise use default
        String imageUrl = imageService.saveProductImage(image, product.getId());
        product.setImageUrl(imageUrl);

        // Save again with the image URL
        product = productRepository.save(product);
        return mapToResponse(product);
    }

    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        updateProductFromRequest(product, request);
        Product savedProduct = productRepository.save(product);
        return mapToResponse(savedProduct);
    }

    public ProductResponse updateProductImage(Long id, MultipartFile image) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // Delete old image if it exists and isn't the default
        imageService.deleteProductImage(product.getImageUrl());

        // Save new image
        String imageUrl = imageService.saveProductImage(image, id);
        product.setImageUrl(imageUrl);

        return mapToResponse(productRepository.save(product));
    }

    private void updateProductFromRequest(Product product, ProductRequest request) {
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setAvailable(request.isAvailable());
    }

    public void updateStock(Long productId, Integer quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        int newStock = product.getStockQuantity() - quantity;
        if (newStock < 0) {
            throw new RuntimeException("Insufficient stock");
        }

        product.setStockQuantity(newStock);
        productRepository.save(product);
    }

    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // Delete product image if it's not the default
        if (!imageService.isDefaultImage(product.getImageUrl())) {
            imageService.deleteProductImage(product.getImageUrl());
        }

        productRepository.delete(product);
    }

    private ProductResponse mapToResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.isAvailable(),
                product.getImageUrl()
        );
    }
}