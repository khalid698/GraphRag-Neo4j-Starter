package com.example.sampleapp;

import org.springframework.stereotype.Repository;

@Repository
public class SampleRepository {

    public String name() {
        return "World";
    }
}
