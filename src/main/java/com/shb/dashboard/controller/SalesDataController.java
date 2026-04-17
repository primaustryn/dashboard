package com.shb.dashboard.controller;

import com.shb.dashboard.model.SalesRecord;
import com.shb.dashboard.service.SalesDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Data ingestion API for the SALES_SUMMARY target table.
 *
 * POST   /api/v1/data/sales         insert a single record
 * POST   /api/v1/data/sales/batch   insert multiple records at once
 * GET    /api/v1/data/sales         list all records (for inspection)
 */
@RestController
@RequestMapping("/api/v1/data/sales")
public class SalesDataController {

    private final SalesDataService salesDataService;

    public SalesDataController(SalesDataService salesDataService) {
        this.salesDataService = salesDataService;
    }

    @PostMapping
    public ResponseEntity<Void> add(@RequestBody SalesRecord record) {
        salesDataService.add(record);
        return ResponseEntity.created(URI.create("/api/v1/data/sales")).build();
    }

    @PostMapping("/batch")
    public ResponseEntity<Void> addBatch(@RequestBody List<SalesRecord> records) {
        salesDataService.addBatch(records);
        return ResponseEntity.created(URI.create("/api/v1/data/sales")).build();
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        return ResponseEntity.ok(salesDataService.getAll());
    }
}
