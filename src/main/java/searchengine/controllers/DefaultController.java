package searchengine.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DefaultController {

    @GetMapping("/")
    public String dashboard(Model model) {
        return "dashboard";
    }

    @GetMapping("/management")
    public String management() {
        return "management";
    }

    @GetMapping("/searchPage")
    public String searchPage() {
        return "search";
    }
}
