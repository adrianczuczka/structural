plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("io.deepmedia.tools.deployer") version "0.17.0"
}

val releaseVersion = "0.2.3"

group = "com.adrianczuczka"
version = releaseVersion

java {
    withSourcesJar()
    withJavadocJar()
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}


gradlePlugin {
    isAutomatedPublishing = true
    plugins {
        create("structural") {
            id = "com.adrianczuczka.structural"
            implementationClass = "com.adrianczuczka.structural.StructuralPlugin"
            displayName = "Structural"
            description =
                "A lightweight Gradle plugin that enforces structured package dependencies."
        }
    }
}

deployer {

    content {
        gradlePluginComponents {}
    }

    projectInfo {
        // Project name. Defaults to rootProject.name
        name.set("structural")
        // Project description. Defaults to rootProject.name
        description.set("A lightweight Gradle plugin that enforces structured package dependencies.")
        // Project url
        url.set("https://github.com/adrianczuczka/structural")
        // Package group id. Defaults to project's group
        groupId.set("com.adrianczuczka")
        // Package artifact. Defaults to project's archivesName or project.name
        artifactId.set("structural")
        // Project SCM information. Defaults to project.url
        scm {
            fromGithub("adrianczuczka", "structural")
        }
        // Licenses. Apache 2.0 and MIT are built-in
        license(apache2)
        // Developers
        developer("Adrian Czuczka", "adrianczuczka@gmail.com")
    }

    centralPortalSpec {
        auth.user.set(secret("MavenCentralUsername"))
        auth.password.set(secret("MavenCentralPassword"))

        signing.key.set(secret("signing.key"))
        signing.password.set(secret("signing.password"))

        allowMavenCentralSync = false
    }

    localSpec {}
}

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.0")
    implementation("org.yaml:snakeyaml:2.3")

    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

tasks.test {
    useJUnitPlatform()
}