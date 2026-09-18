package com.example.supportassistant.orders;

import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class OrderTools {

  private static final Map<String, String> STATUS_BY_ORDER_ID = Map.of(
      "PV-1001", "EM_TRANSITO",
      "PV-1002", "EM_SEPARACAO",
      "PV-1003", "ENTREGUE");

  @Tool(description = "Consulta o status de um pedido da livraria pelo identificador")
  public String orderStatus(
      @ToolParam(description = "Identificador do pedido, no formato PV-1000", required = true) String orderId) {
    String status = STATUS_BY_ORDER_ID.get(orderId);
    if (status == null) {
      return "Pedido não encontrado: " + orderId;
    }
    return "O pedido " + orderId + " está com status " + status + ".";
  }
}