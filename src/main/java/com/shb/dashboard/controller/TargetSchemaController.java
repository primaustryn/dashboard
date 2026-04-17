package com.shb.dashboard.controller;

import com.shb.dashboard.model.SchemaScript;
import com.shb.dashboard.service.TargetSchemaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Executes DDL on the target database at runtime.
 * Use this to create new tables before inserting data or registering widgets.
 *
 * POST /api/v1/target/schema/execute   { "sql": "CREATE TABLE ..." }
 */
@RestController
@RequestMapping("/api/v1/target/schema")
public class TargetSchemaController {

    private final TargetSchemaService targetSchemaService;

    public TargetSchemaController(TargetSchemaService targetSchemaService) {
        this.targetSchemaService = targetSchemaService;
    }

    @PostMapping("/execute")
    public ResponseEntity<Void> execute(@RequestBody SchemaScript script) {
        targetSchemaService.execute(script.getSql());
        return ResponseEntity.ok().build();
    }
}
