package com.shb.dashboard.controller;

import com.shb.dashboard.service.GenericDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Generic row insertion and retrieval for any table in the target database.
 * Column names are derived from the JSON keys of each row object.
 *
 * POST /api/v1/target/{tableName}/rows         insert one row
 * POST /api/v1/target/{tableName}/rows/batch   insert multiple rows
 * GET  /api/v1/target/{tableName}/rows         list all rows
 */
@RestController
@RequestMapping("/api/v1/target/{tableName}/rows")
public class GenericDataController {

    private final GenericDataService genericDataService;

    public GenericDataController(GenericDataService genericDataService) {
        this.genericDataService = genericDataService;
    }

    @PostMapping
    public ResponseEntity<Void> insertRow(
            @PathVariable String tableName,
            @RequestBody Map<String, Object> row) {
        genericDataService.insertRow(tableName, row);
        return ResponseEntity.created(URI.create("/api/v1/target/" + tableName + "/rows")).build();
    }

    @PostMapping("/batch")
    public ResponseEntity<Void> insertBatch(
            @PathVariable String tableName,
            @RequestBody List<Map<String, Object>> rows) {
        genericDataService.insertBatch(tableName, rows);
        return ResponseEntity.created(URI.create("/api/v1/target/" + tableName + "/rows")).build();
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll(@PathVariable String tableName) {
        return ResponseEntity.ok(genericDataService.getAll(tableName));
    }
}
