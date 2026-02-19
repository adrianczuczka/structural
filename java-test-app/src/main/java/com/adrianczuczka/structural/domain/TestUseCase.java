package com.adrianczuczka.structural.domain;

import com.adrianczuczka.structural.data.TestRepository;

public class TestUseCase {
    private final TestRepository repository;

    public TestUseCase(TestRepository repository) {
        this.repository = repository;
    }
}
