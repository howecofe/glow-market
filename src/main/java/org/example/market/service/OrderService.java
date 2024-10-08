package org.example.market.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.market.controller.dto.OrderResponse;
import org.example.market.domain.Member;
import org.example.market.domain.Orders;
import org.example.market.domain.Product;
import org.example.market.exception.*;
import org.example.market.repository.MemberRepository;
import org.example.market.repository.OrderRepository;
import org.example.market.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public Orders findById(Long id) {
        return orderRepository.findById(id).orElseThrow(()->new OrderNotFoundException("존재하지 않는 거래입니다."));
    }

    @Transactional
    public void reserveProduct(Product product, Member buyer, Long price, Long quantity) {

        if (product.getSeller().equals(buyer)) {
            throw new UnauthorizedException("판매자가 본인의 제품을 구매할 수 없습니다.");
        }

        if (product.getStatus() != Product.ProductStatus.FOR_SALE) {
            throw new IllegalStateException("구매할 수 없는 상태입니다.");
        }

        if (!Objects.equals(product.getPrice()*quantity, price*quantity)) {
            throw new IllegalArgumentException("제시한 가격이 일치하지 않습니다.");
        }

        orderRepository.save(new Orders(product, buyer, Orders.OrderStatus.RESERVED,quantity,price*quantity));
    }

    @Transactional
    public void approveSale(Orders orders, Member seller) {
        Product product = productRepository.findById(orders.getProduct().getId())
                .orElseThrow(() -> new ProductNotFoundException("존재하지 않는 상품입니다."));

        if (!product.getSeller().equals(seller)) {
            throw new UnauthorizedException("판매자가 아닙니다.");
        }


        if (product.getStock() < orders.getQuantity()) {
            throw new InsufficientStockException("재고가 부족합니다.");
        }

        if(orders.getQuantity() == product.getStock()) product.soldOut();

        product.minusStock(orders.getQuantity());
        orders.setCompleted();
    }

    public List<OrderResponse> getOrdersByMember(Member member) {

        List<Orders> orders = orderRepository.findByBuyer(member);

        return orders.stream().map(order -> {
            Product product = order.getProduct();
            String productName = (product != null) ? product.getName() : "존재하지 않는 상품";
            return new OrderResponse(
                    order.getId(),
                    productName,
                    order.getTotalPrice(),
                    order.getStatus(),
                    order.getQuantity()
            );
        }).collect(Collectors.toList());
    }

    public List<OrderResponse> getOrdersByProduct(Product product) {
        List<Orders> orders = orderRepository.findByProduct(product);
        return orders.stream().map(order ->
                new OrderResponse(
                        order.getId(),
                        order.getTotalPrice(),
                        order.getQuantity()
                )
        ).collect(Collectors.toList());
    }
}
