package com.sv.io.orderservice.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.sv.io.orderservice.OrderServiceConfig;
import com.sv.io.orderservice.entity.Order;
import com.sv.io.orderservice.external.model.PaymentMode;
import com.sv.io.orderservice.model.OrderRequest;
import com.sv.io.orderservice.model.OrderResponse;
import com.sv.io.orderservice.repository.OrderRepository;
import com.sv.io.orderservice.service.OrderService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.io.IOException;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static java.nio.charset.Charset.defaultCharset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.util.StreamUtils.copyToString;

@SpringBootTest({"server.port=0"})
@EnableConfigurationProperties
@AutoConfigureMockMvc
@ContextConfiguration(classes = {OrderServiceConfig.class})
public class OrderControllerTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private MockMvc mockMvc;

    @RegisterExtension
    static WireMockExtension wireMockServer
            = WireMockExtension.newInstance()
            .options(WireMockConfiguration
                    .wireMockConfig()
                    .port(8080))
            .build();

    private ObjectMapper objectMapper
            = new ObjectMapper()
            .findAndRegisterModules()
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @BeforeEach
    void setUp() throws Exception {
        reduceQuantity();
        doPayment();
        getPaymentDetailsResponse();
        getProductDetailsResponse();
    }

    private OrderRequest getMockOrderRequest() {
        return OrderRequest.builder()
                .productId(1)
                .paymentMode(PaymentMode.CASH)
                .quantity(10)
                .totalAmount(200)
                .build();
    }

    private void reduceQuantity() {
        wireMockServer.stubFor(WireMock.put(urlMatching("/product/reduce-quantity/.*"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)));
    }

    private void doPayment() {
        wireMockServer.stubFor(WireMock.post(urlEqualTo("/payment"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)));
    }

    private void getPaymentDetailsResponse() throws IOException {
        wireMockServer.stubFor(WireMock.get(urlMatching("/payment/.*"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(copyToString(
                                OrderControllerTest.class
                                        .getClassLoader()
                                        .getResourceAsStream("mock/GetPayment.json"),
                                defaultCharset()))));
    }

    private void getProductDetailsResponse() throws IOException {
        wireMockServer.stubFor(WireMock.get(urlMatching("/product/.*"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(copyToString(
                                OrderControllerTest.class
                                        .getClassLoader()
                                        .getResourceAsStream("mock/GetProduct.json"),
                                defaultCharset()))));
    }

    @Test
    public void test_WhenPlaceOrder_DoPayment_Success() throws Exception {
        //First place the order
        OrderRequest orderRequest = getMockOrderRequest();

        MvcResult mvcResult =
                mockMvc.perform(MockMvcRequestBuilders.post("/order/place-order")
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("Customer")))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(orderRequest))
                ).andExpect(MockMvcResultMatchers.status().isOk())
                        .andReturn();

        String orderId = mvcResult.getResponse().getContentAsString();

        //Get order by order id from db and check
        Optional<Order> optionalOrder = orderRepository.findById(Long.valueOf(orderId));
        Assertions.assertEquals(optionalOrder.isPresent(), true);
        Assertions.assertTrue(optionalOrder.isPresent());

        Order order = optionalOrder.get();
        Assertions.assertEquals(Long.parseLong(orderId), order.getId());
        Assertions.assertEquals("PLACED", order.getOrderStatus());
        Assertions.assertEquals(orderRequest.getTotalAmount(), order.getAmount());
        Assertions.assertEquals(orderRequest.getQuantity(), order.getQuantity());
    }

    @Test
    public void test_WhenPlaceOrderWithWrongAccess_ThenThrow403() throws Exception {

        OrderRequest orderRequest = getMockOrderRequest();

        MvcResult mvcResult =
                mockMvc.perform(MockMvcRequestBuilders.post("/order/place-order")
                                .with(jwt()
                                        .authorities(new SimpleGrantedAuthority("Admin")))
                                .contentType(MediaType.APPLICATION_JSON_VALUE)
                                .content(objectMapper.writeValueAsString(orderRequest))
                        ).andExpect(MockMvcResultMatchers.status().isForbidden())
                        .andReturn();
    }

    @Test
    public void test_WhenGetOrder_Success() throws Exception {

        MvcResult mvcResult
                = mockMvc.perform(MockMvcRequestBuilders.get("/order/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("Admin")))
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                        .andExpect(MockMvcResultMatchers.status().isOk())
                        .andReturn();

        String actualResponse = mvcResult.getResponse().getContentAsString();
        Order order = orderRepository.findById(1l).get();
        String expectedResponse = getOrderResponse(order);

        Assertions.assertEquals(expectedResponse, actualResponse);
    }

    @Test
    public void test_WhenGetOrder_Order_Not_Found() throws Exception {

        MvcResult mvcResult =
                mockMvc.perform(MockMvcRequestBuilders.get("/order/2")
                                .with(jwt()
                                        .authorities(new SimpleGrantedAuthority("Admin")))
                                .contentType(MediaType.APPLICATION_JSON_VALUE)
                        ).andExpect(MockMvcResultMatchers.status().isNotFound())
                        .andReturn();

    }

    private String getOrderResponse(Order order) throws IOException {
        OrderResponse.PaymentDetails paymentDetails
                = objectMapper.readValue(
                copyToString(
                        OrderControllerTest.class
                                .getClassLoader()
                                .getResourceAsStream("mock/GetPayment.json"),
                        defaultCharset()),
                OrderResponse.PaymentDetails.class
        );

        paymentDetails.setPaymentStatus("SUCCESS");
        OrderResponse.ProductDetails productDetails
                = objectMapper.readValue(
                copyToString(
                        OrderControllerTest.class
                                .getClassLoader()
                                .getResourceAsStream("mock/GetProduct.json"),
                        defaultCharset()),
                OrderResponse.ProductDetails.class
        );

        OrderResponse orderResponse = OrderResponse.builder()
                .paymentDetails(paymentDetails)
                .productDetails(productDetails)
                .orderStatus(order.getOrderStatus())
                .orderDate(order.getOrderDate())
                .amount(order.getAmount())
                .orderId(order.getId())
                .build();

        return objectMapper.writeValueAsString(orderResponse);
    }

}