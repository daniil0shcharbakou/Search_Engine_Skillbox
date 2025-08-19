package searchengine.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Default controller: отдаёт главный SPA-шаблон index.html при обращении к корню сайта.
 * Важно: не добавляем сюда другие маппинги, чтобы не конфликтовать с ViewController,
 * который отдаёт index для /dashboard, /management, /search (если он у вас есть).
 */
@Controller
public class DefaultController {

    @GetMapping({"/", ""})
    public String dashboard() {
        // Отдаём один и тот же SPA-шаблон, который находится в resources/templates/index.html
        return "index";
    }
}
