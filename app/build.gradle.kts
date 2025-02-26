plugins {
    application
    id("com.adrianczuczka.structural")
}

dependencies {
}

application {
    mainClass = "com.adrianczuczka.structural.ui.TestScreen"
}

structural {
    config = "../structural.yml"
}