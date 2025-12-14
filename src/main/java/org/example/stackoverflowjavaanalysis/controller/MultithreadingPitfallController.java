package org.example.stackoverflowjavaanalysis.controller;

import org.example.stackoverflowjavaanalysis.service.MultithreadingPitfallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/pitfall")
public class MultithreadingPitfallController {

    private static final Logger logger = LoggerFactory.getLogger(MultithreadingPitfallController.class);

    @Autowired
    private MultithreadingPitfallService pitfallService;

    @GetMapping
    public String page(Model model) {
        model.addAttribute("pitfalls", pitfallService.getAllPitfallTypes());
        return "multithreading-pitfall";
    }

    @PostMapping("/data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getData(
            @RequestParam(defaultValue = "2020-01-01") String startDate,
            @RequestParam(defaultValue = "2024-12-31") String endDate,
            @RequestParam(required = false) List<String> selectedPitfalls
    ) {
        try {
            int selectedCount = (selectedPitfalls == null) ? 0 : selectedPitfalls.size();
            logger.info("Get pitfall data: start={}, end={}, selectedCount={}, selected={}", startDate, endDate, selectedCount, selectedPitfalls);
            Map<String, Object> data = pitfallService.getPitfallData(startDate, endDate, selectedPitfalls);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            logger.error("Pitfall analysis failed", e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }
}