package org.example.stackoverflowjavaanalysis.controller;

import org.example.stackoverflowjavaanalysis.service.MainService;
import org.example.stackoverflowjavaanalysis.service.TopicService;
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

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("topQuestions", mainService.getTopScoreQuestions(10));
        return "index";
    }

    @GetMapping("/mode1")
    public String mode1(Model model) {
        topicService.initDefaultTopics();
        model.addAttribute("allTopics", topicService.getAllTopics());
        return "mode1";
    }
}