plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    signing
    id("io.deepmedia.tools.deployer") version "0.17.0"
}

val releaseVersion = findProperty("version") as String

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

    release {
        // Release version. Defaults to project.version, or AGP configured version for Android projects
        release.version.set(releaseVersion)
        // Release VCS tag. Defaults to "v${release.version}"
        release.tag.set("v$releaseVersion")
        // Release description. Defaults to "${project.name} {release.tag}"
        release.description.set("Structural v$releaseVersion")
    }
}


/*
publishing {
    repositories {
        maven {
            name = "MavenCentral"
            url = uri("https://maven.central.sonatype.com/")
            credentials {
                username = findProperty("mavenCentralUsername") as String?
                password = findProperty("mavenCentralPassword") as String?
            }
        }
    }

    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "structural"

            pom {
                name.set("Structural Plugin")
                description.set("A lightweight Gradle plugin that enforces structured package dependencies.")
                url.set("https://github.com/adrianczuczka/structural")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("adrianczuczka")
                        name.set("Adrian Czuczka")
                        email.set("adrianczuczka@gmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/adrianczuczka/structural.git")
                    developerConnection.set("scm:git:ssh://github.com/adrianczuczka/structural.git")
                    url.set("https://github.com/adrianczuczka/structural")
                }
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["mavenJava"])
}
*/

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