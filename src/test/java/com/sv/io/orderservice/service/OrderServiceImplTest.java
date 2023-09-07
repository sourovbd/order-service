package com.sv.io.orderservice.service;

import com.sv.io.orderservice.entity.Order;
import com.sv.io.orderservice.exception.CustomException;
import com.sv.io.orderservice.external.client.PaymentService;
import com.sv.io.orderservice.external.client.ProductService;
import com.sv.io.orderservice.external.entity.Payment;
import com.sv.io.orderservice.external.model.PaymentMode;
import com.sv.io.orderservice.external.model.PaymentRequest;
import com.sv.io.orderservice.external.response.PaymentResponse;
import com.sv.io.orderservice.external.response.ProductResponse;
import com.sv.io.orderservice.model.OrderRequest;
import com.sv.io.orderservice.model.OrderResponse;
import com.sv.io.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@SpringBootTest
public class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductService productService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private RestTemplate restTemplate;
    @InjectMocks
    OrderService orderService = new OrderServiceImpl();

    private Order getMockOrder() {
        return Order.builder()
                .orderStatus("PLACED")
                .orderDate(Instant.now())
                .id(1)
                .amount(100)
                .quantity(200)
                .productId(2)
                .build();
    }

    private OrderRequest getMockOrderRequest() {
        return OrderRequest.builder()
                .productId(1)
                .quantity(10)
                .paymentMode(PaymentMode.CASH)
                .totalAmount(100)
                .build();
    }

    private ProductResponse getMockProductResponse() {
        return ProductResponse.builder()
                .productId(2)
                .productName("iPhone")
                .price(1100)
                .quantity(10)
                .build();
    }

    private PaymentResponse getMockPaymentResponse() {
        return PaymentResponse.builder()
                .paymentId(1)
                .orderId(1)
                .paymentMode(PaymentMode.CASH)
                .paymentDate(Instant.now())
                .amount(200)
                .status("ACCEPTED")
                .build();
    }

    private Payment getMockPayment() {
        return Payment.builder()
                .orderId(1)
                .paymentStatus("ACCEPTED")
                .paymentDate(Instant.now())
                .paymentMode(PaymentMode.CASH.name())
                .amount(200)
                .id(1)
                .build();

    }

    @DisplayName("Get Order - Success")
    @Test
    void test_When_Get_Order_Success() {
        //Arrange: Mocking for internal method call.
        Order order = getMockOrder();
        when(orderRepository.findById(anyLong()))
                .thenReturn(Optional.of(order));

        ProductResponse productResponse = getMockProductResponse();
        when( restTemplate.getForObject("http://PRODUCT-SERVICE/product/"+order.getProductId(),
                ProductResponse.class))
                .thenReturn(productResponse);

        PaymentResponse paymentResponse = getMockPaymentResponse();
        when(restTemplate.getForObject("http://PAYMENT-SERVICE/payment/order/"+order.getId(),
                PaymentResponse.class))
                .thenReturn(paymentResponse);
        //Act: Actual method call.
        OrderResponse orderResponse = orderService.getOrderDetails(1);

        //verification
        verify(orderRepository, times(1)).findById(anyLong());
        verify(restTemplate, times(1)).getForObject("http://PRODUCT-SERVICE/product/"+order.getProductId(),
                ProductResponse.class);
        verify(restTemplate, times(1)).getForObject("http://PAYMENT-SERVICE/payment/order/"+order.getId(),
                PaymentResponse.class);
        //Assert:
        assertNotNull(orderResponse);
        assertEquals(order.getId(), orderResponse.getOrderId());
    }

    @DisplayName("Get Order - Failure")
    @Test
    void test_When_Get_Order_Failure() {

        //Arrange: Mocking for internal method call.
        when(orderRepository.findById(anyLong()))
                .thenReturn(Optional.ofNullable(null));

        //Act: Actual method call.

        //Assert:
        CustomException exception = assertThrows(CustomException.class,
                () -> orderService.getOrderDetails(1));
        assertEquals("ORDER_NOT_FOUND", exception.getErrorCode());
        assertEquals(404, exception.getStatus());

        verify(orderRepository, times(1))
                .findById(anyLong());

    }

    @DisplayName("Place Order - Success")
    @Test
    public void testPlaceOrderSuccess() {
        Order order = getMockOrder();
        Payment payment = getMockPayment();
        //PaymentRequest paymentRequest = getMockPaymentRequest();
        OrderRequest orderRequest = getMockOrderRequest();

        // Mock behavior of productService, orderRepository, and paymentService
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(productService.reduceQuantity(anyLong(),anyLong()))
                .thenReturn(new ResponseEntity<Void>(HttpStatus.OK));
        when(paymentService.doPayment(any(PaymentRequest.class)))
                .thenReturn(new ResponseEntity<Long>(1L, HttpStatus.OK));

        // Call the method being tested
        long orderId = orderService.placeOrder(orderRequest);

        // Assertions
        verify(orderRepository, times(2))
                .save(any());
        verify(productService, times(1))
                .reduceQuantity(anyLong(),anyLong());
        verify(paymentService, times(1))
                .doPayment(any(PaymentRequest.class));

        assertEquals(1L, orderId); // Assuming the method returns an order ID
    }

    @DisplayName("Place Order - Payment Failed Scenario")
    @Test
    public void testPlaceOrderFailure() {
        Order order = getMockOrder();
        Payment payment = getMockPayment();
        //PaymentRequest paymentRequest = getMockPaymentRequest();
        OrderRequest orderRequest = getMockOrderRequest();

        // Mock behavior of productService, orderRepository, and paymentService
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(productService.reduceQuantity(anyLong(),anyLong()))
                .thenReturn(new ResponseEntity<Void>(HttpStatus.OK));
        when(paymentService.doPayment(any(PaymentRequest.class)))
                .thenThrow(new RuntimeException());

        // Call the method being tested
        long orderId = orderService.placeOrder(orderRequest);

        // Assertions
        verify(orderRepository, times(2))
                .save(any());
        verify(productService, times(1))
                .reduceQuantity(anyLong(),anyLong());
        verify(paymentService, times(1))
                .doPayment(any(PaymentRequest.class));

        assertEquals(1L, orderId); // Assuming the method returns an order ID
    }
}