package searchengine.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DefaultController {

    @GetMapping({"/", ""})
    public String dashboard() {
        return "index";
    }
}
