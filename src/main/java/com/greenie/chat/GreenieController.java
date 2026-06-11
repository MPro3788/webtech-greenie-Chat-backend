package com.greenie.chat;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/data")
    public List<Greenie> getAllData() {
        return StreamSupport.stream(greenieRepository.findAll().spliterator(), false).toList();
    }

    @GetMapping("/data/{id}")
    public Greenie getDataById(@PathVariable Long id) {
        return greenieRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Datensatz nicht gefunden: " + id));
    }

    @PostMapping("/data")
    public Greenie createData(@RequestBody Greenie greenie) {
        if (greenie.getId() == null) {
            greenie.setId(nextId());
        }
        return greenieRepository.save(greenie);
    }

    @PostMapping("/data/{id}")
    public Greenie createDataById(@PathVariable Long id, @RequestBody Greenie greenie) {
        greenie.setId(id);
        return greenieRepository.save(greenie);
    }

    private Long nextId() {
        return StreamSupport.stream(greenieRepository.findAll().spliterator(), false)
                .map(Greenie::getId)
                .max(Long::compareTo)
                .orElse(0L) + 1;
    }
}
