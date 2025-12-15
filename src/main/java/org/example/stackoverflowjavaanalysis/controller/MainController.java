package org.example.stackoverflowjavaanalysis.controller;

import org.example.stackoverflowjavaanalysis.service.MainService;
import org.example.stackoverflowjavaanalysis.service.TopicService;
import org.example.stackoverflowjavaanalysis.service.MultithreadingPitfallService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainController {
    @Autowired
    private MainService mainService;
    @Autowired
    private TopicService topicService;
    @Autowired
    private MultithreadingPitfallService multithreadingPitfallService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("stats", mainService.getDatabaseStats());
        model.addAttribute("topQuestions", mainService.getTopScoreQuestions(10));
        return "index";
    }

    @GetMapping("/mode1")
    public String mode1(Model model) {
        topicService.initDefaultTopics();
        model.addAttribute("allTopics", topicService.getAllTopics());
        return "mode1";
    }

    @GetMapping("/multithreadingPitfall")
    public String multithreadingPitfall(Model model) {
        // 这里可以添加需要的初始化数据
        model.addAttribute("pitfallTypes", multithreadingPitfallService.getAllPitfallTypes());
        return "multithreadingPitfall";
    }
}