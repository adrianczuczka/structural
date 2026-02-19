package com.adrianczuczka.structural.ui;

import com.adrianczuczka.structural.data.TestRepository;

public class TestViewModel {
    private final TestRepository repository;

    public TestViewModel(TestRepository repository) {
        this.repository = repository;
    }
}
