package com.example.inventory.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.inventory.catalog.CatalogSnapshotService;
import com.example.inventory.imports.InventoryImportService;
import com.example.inventory.vendor.Price;
import com.example.inventory.vendor.PriceLookupService;
import com.example.inventory.vendor.SupplierOrderService;

@RestController
@RequestMapping("/api")
public class InventoryController {

  private final InventoryImportService importService;
  private final PriceLookupService priceLookupService;
  private final SupplierOrderService supplierOrderService;
  private final CatalogSnapshotService snapshotService;

  public InventoryController(InventoryImportService importService,
                             PriceLookupService priceLookupService,
                             SupplierOrderService supplierOrderService,
                             CatalogSnapshotService snapshotService) {
    this.importService = importService;
    this.priceLookupService = priceLookupService;
    this.supplierOrderService = supplierOrderService;
    this.snapshotService = snapshotService;
  }

  @PostMapping("/inventory/import")
  public ResponseEntity<Void> importCatalog(@RequestParam String source) {
    importService.importInBackground(source);
    return ResponseEntity.accepted().build();
  }

  @GetMapping("/prices/{productId}")
  public Price price(@PathVariable Long productId) {
    return priceLookupService.findPrice(productId);
  }

  @PostMapping("/supplier-orders")
  public ResponseEntity<Void> placeOrder(@RequestBody SupplierOrderService.OrderRequest request) {
    supplierOrderService.placeOrder(request);
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/catalog/snapshot/{productId}")
  public ResponseEntity<Void> rebuildSnapshot(@PathVariable Long productId) {
    snapshotService.rebuild(productId);
    return ResponseEntity.accepted().build();
  }
}