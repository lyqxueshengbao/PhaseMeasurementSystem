package com.example.pj125.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UiController {
    @GetMapping({"/", "/ui"})
    public String home() {
        return "redirect:/ui/run";
    }

    @GetMapping("/ui/devices")
    public String devices() {
        return "devices";
    }

    @GetMapping("/ui/recipes")
    public String recipes() {
        return "recipes";
    }

    @GetMapping("/ui/run")
    public String run() {
        return "run";
    }
}

