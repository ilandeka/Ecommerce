package com.eshop.service;

import com.eshop.model.dto.OrderResponse;
import com.eshop.model.entity.*;
import com.eshop.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final ProductService productService;

    public OrderService(OrderRepository orderRepository, CartService cartService, ProductService productService) {
        this.orderRepository = orderRepository;
        this.cartService = cartService;
        this.productService = productService;
    }

    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
    }

    public void save(Order order) {
        orderRepository.save(order);
    }

    public Order checkout(Long userId, ShippingInfo shippingInfo) {
        // Get the user's cart
        Cart cart = cartService.getCart(userId);
        if (cart.getItems().isEmpty()) {
            throw new RuntimeException("Cart is empty");
        }

        // Create order from cart
        Order order = new Order();
        order.setUser(cart.getUser());
        order.setTotal(cart.getTotal());
        order.setShippingInfo(shippingInfo);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);

        // Convert cart items to order items
        for (CartItem cartItem : cart.getItems()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPrice(cartItem.getPrice());
            order.getItems().add(orderItem);

            // Update product stock
            productService.updateStock(cartItem.getProduct().getId(),
                    cartItem.getQuantity());
        }

        // Save the order
        order = orderRepository.save(order);

        // Clear the cart
        cartService.clearCart(userId);

        return order;
    }

    public Page<OrderResponse> getUserOrders(Long userId, Pageable pageable) {
        Page<Order> orderPage = orderRepository.findByUserId(userId, pageable);

        return orderPage.map(order -> {
            OrderResponse response = new OrderResponse();
            response.setId(order.getId());
            response.setTotal(order.getTotal());
            response.setStatus(order.getStatus());
            response.setPaymentStatus(order.getPaymentStatus());
            response.setCreatedAt(order.getCreatedAt());

            // Map items
            List<OrderResponse.OrderItemDTO> items = order.getItems().stream()
                    .map(item -> {
                        OrderResponse.OrderItemDTO dto = new OrderResponse.OrderItemDTO();
                        dto.setProductName(item.getProduct().getName());
                        dto.setQuantity(item.getQuantity());
                        dto.setPrice(item.getPrice());
                        dto.setSubtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                        return dto;
                    }).collect(Collectors.toList());
            response.setItems(items);

            // Map shipping info
            if (order.getShippingInfo() != null) {
                OrderResponse.ShippingInfoDTO shippingDTO = new OrderResponse.ShippingInfoDTO();
                shippingDTO.setFullName(order.getShippingInfo().getFullName());
                shippingDTO.setAddress(order.getShippingInfo().getAddress());
                shippingDTO.setCity(order.getShippingInfo().getCity());
                shippingDTO.setState(order.getShippingInfo().getState());
                shippingDTO.setZipCode(order.getShippingInfo().getZipCode());
                response.setShippingInfo(shippingDTO);
            }

            return response;
        });
    }
}