package com.backend.code.editor.controller;

import com.backend.code.editor.request.ExecuteRequest;
import com.backend.code.editor.response.ExecuteResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ExecutionController {

    @PostMapping("execute")
    public ResponseEntity<ExecuteResponse> execute(@Valid @RequestBody ExecuteRequest executeRequest) {
        ExecuteResponse fake = ExecuteResponse.success(
                "echo: " + executeRequest.code(), "", 0, 0);
                return ResponseEntity.ok(fake);
    }
}
