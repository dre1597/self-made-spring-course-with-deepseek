package com.example.shop.order;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

  private final OrderRepository repository;
  private final ApplicationEventPublisher eventPublisher;

  public OrderService(OrderRepository repository, ApplicationEventPublisher eventPublisher) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public Order create(CreateOrderRequest request) {
    Order order = repository.save(new Order(request.item(), request.amount()));
    eventPublisher.publishEvent(new OrderPlaced(order.getId(), order.getItem(), order.getAmount()));
    return order;
  }
}