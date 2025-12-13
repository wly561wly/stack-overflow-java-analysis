package org.example.stackoverflowjavaanalysis.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import jakarta.servlet.http.HttpServletRequest; // 确保使用 jakarta

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(HttpServletRequest req, Exception ex) {
        // Suppress noisy favicon missing errors (avoid spamming logs)
        if (ex instanceof NoResourceFoundException && "/favicon.ico".equals(req.getRequestURI())) {
            // Return 204 No Content silently
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }

        logger.error("Unhandled exception, request URI: {}", req.getRequestURI(), ex);
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Internal server error: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // 可选：当浏览器请求 HTML 页面时返回自定义错误页面
    @ExceptionHandler({IllegalStateException.class})
    public String handleHtmlException(Exception ex, Model model) {
        logger.warn("HTML exception: {}", ex.getMessage());
        model.addAttribute("message", ex.getMessage());
        return "error";
    }
}