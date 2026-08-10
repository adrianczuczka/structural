plugins {
    application
    id("com.adrianczuczka.structural")
    kotlin("jvm")
}

dependencies {
}

application {
    mainClass = "com.adrianczuczka.structural.ui.TestScreen"
}

structural {
    config.set(file("$rootDir/structural/structural.yml"))
    baseline.set(file("$rootDir/structural/baseline.xml"))
}