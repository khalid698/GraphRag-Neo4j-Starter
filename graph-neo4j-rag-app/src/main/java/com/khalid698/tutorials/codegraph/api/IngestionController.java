package com.khalid698.tutorials.codegraph.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.khalid698.tutorials.codegraph.api.dto.IngestRequest;
import com.khalid698.tutorials.codegraph.api.dto.IngestResponse;
import com.khalid698.tutorials.codegraph.ingest.IngestionService;

@RestController
@RequestMapping("/api/v1")
@Validated
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingest(@RequestBody IngestRequest request) {
        if (request == null || request.moduleName() == null || request.repoPath() == null) {
            return ResponseEntity.badRequest().build();
        }
        boolean includeTests = request.options() != null && Boolean.TRUE.equals(request.options().includeTests());
        Integer chunkChars = request.options() != null ? request.options().chunkChars() : null;
        Integer overlap = request.options() != null ? request.options().overlap() : null;
        boolean embed = request.options() == null || request.options().embed() == null || request.options().embed();

        IngestionService.Summary summary = ingestionService.ingest(
                request.repoPath(),
                request.moduleName(),
                includeTests,
                chunkChars,
                overlap,
                embed);

        IngestResponse response = new IngestResponse(
                summary.moduleName(),
                new IngestResponse.Counts(summary.types(), summary.methods(), summary.endpoints(), summary.chunks(), summary.relationships()),
                summary.durationMs()
        );
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handle(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
    }
}
