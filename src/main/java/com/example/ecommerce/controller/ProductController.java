package com.example.ecommerce.controller;

import com.example.ecommerce.model.dto.ProductRequest;
import com.example.ecommerce.model.dto.ProductResponse;
import com.example.ecommerce.service.ProductService;
import com.example.ecommerce.util.SortingUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/public")
    public Page<ProductResponse> getAllProducts(
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        // Validate sort field
        String field = sort.split(",")[0];
        SortingUtils.validateSortField(field, SortingUtils.ALLOWED_PRODUCT_FIELDS);

        // Create sort using the simpler method
        Sort sorting = SortingUtils.createSort(sort);
        Pageable pageable = PageRequest.of(0, 20, sorting);

        return productService.getAllProducts(pageable);
    }

    @GetMapping("/public/{id}")
    public ProductResponse getProduct(@PathVariable Long id) {
        return productService.getProduct(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(
            @RequestPart("product") @Valid ProductRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {

        return ResponseEntity.ok(productService.createProduct(request, image));
    }

    @PatchMapping("/{id}/image")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> updateProductImage(
            @PathVariable Long id,
            @RequestPart("image") MultipartFile image) {

        return ResponseEntity.ok(productService.updateProductImage(id, image));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse updateProduct(@PathVariable Long id,
                                         @Valid @RequestBody ProductRequest request) {
        return productService.updateProduct(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok().build();
    }
}
