package com.example.sampleapp;

import org.springframework.stereotype.Service;

@Service
public class SampleService {

    private final SampleRepository repository;

    public SampleService(SampleRepository repository) {
        this.repository = repository;
    }

    public String greet() {
        return "Hello, " + repository.name();
    }
}
