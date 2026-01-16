package com.khalid698.tutorials.codegraph.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.khalid698.tutorials.codegraph.ai.QueryService;
import com.khalid698.tutorials.codegraph.api.dto.QueryRequest;
import com.khalid698.tutorials.codegraph.ai.dto.QueryResponseDTO;

@RestController
@RequestMapping("/api/v1")
@Validated
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    @PostMapping("/query")
    public ResponseEntity<QueryResponseDTO> query(@RequestBody QueryRequest request) {
        if (request == null || request.question() == null) {
            return ResponseEntity.badRequest().build();
        }
        int topK = request.topK() != null ? request.topK() : 10;
        int hops = request.hops() != null ? request.hops() : 2;
        boolean generate = request.generateAnswer() == null || request.generateAnswer();
        QueryResponseDTO response = queryService.query(request.question(), request.moduleName(), topK, hops, generate);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handle(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
    }
}
