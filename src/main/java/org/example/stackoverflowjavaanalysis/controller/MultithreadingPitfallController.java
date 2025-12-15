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
            @RequestParam(required = false) List<String> selectedPitfalls,
            @RequestParam(required = false, defaultValue = "") String customWords,
            @RequestParam(required = false, defaultValue = "count") String lineChartAttribute
    ) {
        try {
            int selectedCount = (selectedPitfalls == null) ? 0 : selectedPitfalls.size();
            logger.info("Get pitfall data: start={}, end={}, selectedCount={}, selected={}, customWords={}, lineChartAttribute={}",
                    startDate, endDate, selectedCount, selectedPitfalls, customWords, lineChartAttribute);

            List<String> customWordsList = new ArrayList<>();
            List<String> errorMessages = new ArrayList<>();

            if (customWords != null && !customWords.isEmpty()) {
                String[] words = customWords.split("\\r?\\n");
                Set<String> validPitfallTypes = pitfallService.getAllPitfallTypes();
                for (String word : words) {
                    if (word.trim().isEmpty()) continue;
                    if (word.contains(":") && validPitfallTypes.contains(word.split(":")[0])) {
                        customWordsList.add(word);
                    } else if (!word.contains(":")) {
                        errorMessages.add("格式错误: " + word + " (应为 '陷阱类型:关键词')");
                    } else {
                        errorMessages.add("未知陷阱类型: " + word);
                    }
                }
            }

            Map<String, Object> data = pitfallService.getPitfallData(startDate, endDate, selectedPitfalls, customWordsList, lineChartAttribute);
            // 添加验证错误信息
            if (!errorMessages.isEmpty()) {
                data.put("validationErrors", errorMessages);
            }

            return ResponseEntity.ok(data);
        } catch (Exception e) {
            logger.error("Pitfall analysis failed", e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }
}