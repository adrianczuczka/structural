package com.adrianczuczka.structural.data

import com.adrianczuczka.structural.data.local.LocalDataSource
import com.adrianczuczka.structural.data.remote.RemoteDataSource
import com.adrianczuczka.structural.domain.TestUseCase
import com.adrianczuczka.structural.ui.TestViewModel

class TestRepository(
    useCase: TestUseCase,
    localDataSource: LocalDataSource,
    remoteDataSource: RemoteDataSource,
    viewModel: TestViewModel,
)