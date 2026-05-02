plugins {
    id("java")
}

allprojects {
    group = "dev.noah.pluginlock"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    val integrationTestSourceSet = sourceSets.create("integrationTest") {
        java.srcDir("src/integrationTest/java")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }

    configurations.named(integrationTestSourceSet.implementationConfigurationName) {
        extendsFrom(configurations.testImplementation.get())
    }

    configurations.named(integrationTestSourceSet.runtimeOnlyConfigurationName) {
        extendsFrom(configurations.testRuntimeOnly.get())
    }

    val integrationTest by tasks.registering(Test::class) {
        description = "Runs integration tests."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        testClassesDirs = integrationTestSourceSet.output.classesDirs
        classpath = integrationTestSourceSet.runtimeClasspath
        shouldRunAfter(tasks.named("test"))
        useJUnitPlatform {
            excludeTags("live")
        }
    }

    tasks.register<Test>("liveIntegrationTest") {
        description = "Runs integration tests that call real external APIs."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        testClassesDirs = integrationTestSourceSet.output.classesDirs
        classpath = integrationTestSourceSet.runtimeClasspath
        shouldRunAfter(integrationTest)
        useJUnitPlatform {
            includeTags("live")
        }
        onlyIf {
            providers.gradleProperty("liveApi").orNull == "true"
        }
    }

    tasks.named("check") {
        dependsOn(integrationTest)
    }
}
