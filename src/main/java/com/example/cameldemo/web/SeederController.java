package com.example.cameldemo.web;

import com.example.cameldemo.seeder.DataSeederService;
import com.example.cameldemo.seeder.SeederResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the Oracle data seeder.
 *
 *   POST /api/seed?count=N   — insert N fake rows into BANK_TRANSACTIONS
 *   GET  /api/seed/count     — return current row count (no side effects)
 */
@RestController
@RequestMapping("/api/seed")
public class SeederController {

    private static final Logger log = LoggerFactory.getLogger(SeederController.class);

    private final DataSeederService seederService;

    public SeederController(DataSeederService seederService) {
        this.seederService = seederService;
    }

    /**
     * Inserts {@code count} fake banking records into Oracle.
     *
     * @param count number of records to generate (required, 1–10 000 000)
     */
    @PostMapping
    public ResponseEntity<?> seed(@RequestParam int count) {
        if (count < 1 || count > 10_000_000) {
            return ResponseEntity.badRequest()
                    .body("count must be between 1 and 10 000 000");
        }
        try {
            SeederResult result = seederService.seed(count);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Seeding failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    /** Returns the current number of rows in BANK_TRANSACTIONS (no writes). */
    @GetMapping("/count")
    public ResponseEntity<Long> count() {
        return ResponseEntity.ok(seederService.countRows());
    }
}
