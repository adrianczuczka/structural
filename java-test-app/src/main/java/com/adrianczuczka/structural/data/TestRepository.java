package com.adrianczuczka.structural.data;

import com.adrianczuczka.structural.data.local.LocalDataSource;
import com.adrianczuczka.structural.data.remote.RemoteDataSource;
import com.adrianczuczka.structural.domain.TestUseCase;
import com.adrianczuczka.structural.ui.TestViewModel;

public class TestRepository {
    private final TestUseCase useCase;
    private final LocalDataSource localDataSource;
    private final RemoteDataSource remoteDataSource;
    private final TestViewModel viewModel;

    public TestRepository(
            TestUseCase useCase,
            LocalDataSource localDataSource,
            RemoteDataSource remoteDataSource,
            TestViewModel viewModel) {
        this.useCase = useCase;
        this.localDataSource = localDataSource;
        this.remoteDataSource = remoteDataSource;
        this.viewModel = viewModel;
    }
}
