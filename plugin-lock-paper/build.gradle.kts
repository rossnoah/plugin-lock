plugins {
    java
}

dependencies {
    implementation(project(":plugin-lock-core"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    archiveBaseName.set("plugin-lock-paper")
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
