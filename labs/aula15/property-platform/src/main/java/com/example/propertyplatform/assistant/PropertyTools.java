package com.example.propertyplatform.assistant;

import java.math.BigDecimal;
import java.util.List;

import com.example.propertyplatform.listing.PropertyCatalog;
import com.example.propertyplatform.listing.PropertySummary;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class PropertyTools {

  private final PropertyCatalog propertyCatalog;

  public PropertyTools(PropertyCatalog propertyCatalog) {
    this.propertyCatalog = propertyCatalog;
  }

  @Tool(description = "Lista imóveis disponíveis para aluguel por cidade e teto de aluguel mensal")
  public List<PropertySummary> findAvailable(
      @ToolParam(description = "Cidade do imóvel; use vazio para qualquer cidade") String city,
      @ToolParam(description = "Aluguel mensal máximo em reais") double maxMonthlyRent) {
    return propertyCatalog.findAvailable(city, BigDecimal.valueOf(maxMonthlyRent));
  }
}