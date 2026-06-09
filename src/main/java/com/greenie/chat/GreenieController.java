package com.greenie.chat;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.StreamSupport;

@CrossOrigin(origins = "*")
@RestController
public class GreenieController {

    private final GreenieRepository greenieRepository;

    public GreenieController(GreenieRepository greenieRepository) {
        this.greenieRepository = greenieRepository;
    }

    @GetMapping("/")
    public String helloWorld() {
        return "Hello World";
    }

    @GetMapping("/data/{id}")
    public List<Greenie> getAllData() {
        return StreamSupport.stream(greenieRepository.findAll().spliterator(), false).toList();
    }
    /*public GreenieController getData(@PathVariable String id) {
        Long thingId = Long.parseLong(id);
        return greenieRepository.get(thingId);
    }*/                                         //Testphrase

    @PostMapping("/data")
    public Greenie createData(@RequestBody Greenie greenie) {
        return greenieRepository.save(greenie);
    }
}
