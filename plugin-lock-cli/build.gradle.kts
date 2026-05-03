plugins {
    application
}

import org.gradle.internal.os.OperatingSystem

dependencies {
    implementation(project(":plugin-lock-core"))
    implementation("info.picocli:picocli:4.7.6")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("dev.noah.pluginlock.cli.PluginLockCli")
    applicationName = "pl"
}

val packageInputDir = layout.buildDirectory.dir("jpackage/input")
val packageOutputDir = layout.buildDirectory.dir("jpackage/output")
val nativePackageVersion = providers.gradleProperty("nativePackageVersion").orElse("1.0.0")

tasks.register<Jar>("fatJar") {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Builds a standalone CLI jar for native packaging."
    archiveBaseName.set("pl")
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
    dependsOn(configurations.runtimeClasspath)
}

tasks.register<Sync>("prepareJpackageInput") {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Prepares the jpackage input directory."
    dependsOn(tasks.named("fatJar"))
    from(tasks.named<Jar>("fatJar")) {
        rename { "pl.jar" }
    }
    into(packageInputDir)
}

tasks.register<Delete>("cleanJpackageOutput") {
    delete(packageOutputDir)
}

tasks.register<Exec>("jpackageImage") {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Builds a self-contained native app image for the pl CLI."
    dependsOn("prepareJpackageInput", "cleanJpackageOutput")

    doFirst {
        packageOutputDir.get().asFile.mkdirs()
    }

    commandLine(
        "jpackage",
        "--type", "app-image",
        "--name", "pl",
        "--app-version", nativePackageVersion.get(),
        "--vendor", "Noah Ross",
        "--input", packageInputDir.get().asFile.absolutePath,
        "--main-jar", "pl.jar",
        "--main-class", application.mainClass.get(),
        "--dest", packageOutputDir.get().asFile.absolutePath,
        "--java-options", "-Dfile.encoding=UTF-8"
    )
}

tasks.register<Exec>("jpackageInstaller") {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Builds a native installer for the pl CLI on the current OS."
    dependsOn("prepareJpackageInput", "cleanJpackageOutput")

    val os = OperatingSystem.current()
    val installerType = providers.gradleProperty("installerType").orElse(
        when {
            os.isMacOsX -> "pkg"
            os.isWindows -> "msi"
            else -> "deb"
        }
    )

    doFirst {
        packageOutputDir.get().asFile.mkdirs()
    }

    commandLine(
        "jpackage",
        "--type", installerType.get(),
        "--name", "pl",
        "--app-version", nativePackageVersion.get(),
        "--vendor", "Noah Ross",
        "--input", packageInputDir.get().asFile.absolutePath,
        "--main-jar", "pl.jar",
        "--main-class", application.mainClass.get(),
        "--dest", packageOutputDir.get().asFile.absolutePath,
        "--java-options", "-Dfile.encoding=UTF-8"
    )
}
