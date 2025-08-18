package searchengine.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;


@Controller
public class ViewController {

    @GetMapping({"/dashboard", "/management", "/search"})
    public String index() {
        return "index";
    }
}
