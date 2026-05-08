package com.greenie.chat;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class GreenieController {

    @GetMapping("/")
    public String helloWorld() {
        return "Hello World";
    }

    @GetMapping("/data")
    public List<Data> getAllData() {
        return List.of(
                new Data("Max", "Entwickler", 28),
                new Data("Sofia", "Designer", 32),
                new Data("Ali", "Produktmanager", 41)
        );
    }

    public record Data(String user, String beruf, int alter) {}

    @PostMapping("/data")
    public Data createData(@RequestBody Data data) {
        return new Data(data.user(), data.beruf(), data.alter());
    }
}
