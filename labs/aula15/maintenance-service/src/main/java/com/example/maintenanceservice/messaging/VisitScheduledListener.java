package com.example.maintenanceservice.messaging;

import com.example.maintenanceservice.orders.WorkOrderService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class VisitScheduledListener {

  private static final Logger logger = LoggerFactory.getLogger(VisitScheduledListener.class);

  private final WorkOrderService workOrderService;

  public VisitScheduledListener(WorkOrderService workOrderService) {
    this.workOrderService = workOrderService;
  }

  @RabbitListener(queues = "visit-scheduled")
  public void on(VisitScheduledMessage message) {
    workOrderService.open(
        String.valueOf(message.propertyId()),
        "Vistoria preventiva antes da visita " + message.visitId(),
        "LOW");
    logger.info("Vistoria preventiva aberta para o imóvel {}", message.propertyId());
  }
}