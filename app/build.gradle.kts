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
    config = "$rootDir/structural.yml"
}