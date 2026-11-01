plugins {
    `java-library`
    id("net.neoforged.moddev") version "2.0.78"
}

group = "net.minecomputers"
version = "0.1.0"

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

repositories {
    mavenCentral()
    // Create, Ponder and Flywheel (see https://wiki.createmod.net/developers/depend-on-create/neoforge-1.21.1).
    maven {
        name = "Create"
        url = uri("https://maven.createmod.net")
    }
    // Registrate, which Create needs.
    maven {
        name = "Registrate"
        url = uri("https://maven.ithundxr.dev/snapshots")
    }
}

neoForge {
    version = property("neo_version") as String

    runs {
        create("client") {
            client()
        }
        create("server") {
            server()
            programArgument("--nogui")
        }
    }

    mods {
        create("minecomputers") {
            sourceSet(sourceSets.main.get())
        }
    }
}

val graalVersion = property("graal_version") as String
// Create for Minecraft 1.21.1 (versions from Create's own "Depending on Create" page).
val createVersion = "6.0.10-280"
val ponderVersion = "1.0.82"
val flywheelVersion = "1.0.6"
val registrateVersion = "MC1.21-1.3.0+67"

// JavaCV's own jar has a module descriptor that requires OpenCV, librealsense and a dozen other native modules
// that we do not ship (the game then refuses to start: "Module org.bytedeco.librealsense not found").
// So we repack the jar without module-info: it becomes a plain library that only needs what we use (FFmpeg).
val javacvSource = configurations.detachedConfiguration(
    (dependencies.create("org.bytedeco:javacv:1.5.10") as ModuleDependency).also { it.isTransitive = false }
)
val javacvPlain = tasks.register<Zip>("javacvPlain") {
    from(zipTree(javacvSource.singleFile)) {
        exclude("module-info.class", "META-INF/versions/**", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
    }
    archiveFileName.set("javacv-plain.jar")
    destinationDirectory.set(layout.buildDirectory.dir("javacv"))
}

dependencies {
    // GraalJS runs the in-game JavaScript (milestone 2).
    // NeoForge needs the libraries on BOTH the compile classpath and the runtime classpath,
    // otherwise it compiles but throws NoClassDefFoundError at runtime.
    // We use the real jars (polyglot + js-language) and NOT org.graalvm.polyglot:js-community: that one is a
    // POM-only artifact, and its .pom file ended up on the game classpath, where FML tried to open it as a
    // jar and crashed with java.nio.file.ProviderNotFoundException (suspected cause, being tested).
    // js-language pulls in truffle-api, regex and the rest through its own dependencies.
    implementation("org.graalvm.polyglot:polyglot:$graalVersion")
    implementation("org.graalvm.js:js-language:$graalVersion")
    // String form: the plugin doesn't generate a type-safe Kotlin accessor for this configuration.
    "additionalRuntimeClasspath"("org.graalvm.polyglot:polyglot:$graalVersion")
    "additionalRuntimeClasspath"("org.graalvm.js:js-language:$graalVersion")

    // JLayer: pure Java MP3 decoder for the Speaker (client side). Same two lines as GraalJS: compile + runtime.
    // Not bundled into the mod jar yet (jar-in-jar is still to do for GraalJS as well).
    implementation("javazoom:jlayer:1.0.1")
    "additionalRuntimeClasspath"("javazoom:jlayer:1.0.1")

    // FFmpeg (JavaCV) for video and for every audio format except MP3 (client side).
    // javacv itself is non-transitive on purpose: its default dependencies pull in OpenCV and more (hundreds of MB).
    // Natives: Linux, Windows and macOS (Apple silicon). For release, jar-in-jar them with GraalJS and JLayer.
    val javacppVersion = "1.5.10"
    val ffmpegVersion = "6.1.1-$javacppVersion"
    implementation(files(javacvPlain))
    implementation("org.bytedeco:javacpp:$javacppVersion")
    implementation("org.bytedeco:ffmpeg:$ffmpegVersion")
    "additionalRuntimeClasspath"(files(javacvPlain))
    "additionalRuntimeClasspath"("org.bytedeco:javacpp:$javacppVersion")
    "additionalRuntimeClasspath"("org.bytedeco:ffmpeg:$ffmpegVersion")
    // Development: only Linux natives (fewer jars on the module path). For a release build run Gradle with
    // -PallPlatforms to add Windows and macOS.
    val nativePlatforms = if (project.hasProperty("allPlatforms")) {
        listOf("linux-x86_64", "windows-x86_64", "macosx-arm64")
    } else {
        listOf("linux-x86_64")
    }
    for (platform in nativePlatforms) {
        runtimeOnly("org.bytedeco:javacpp:$javacppVersion:$platform")
        runtimeOnly("org.bytedeco:ffmpeg:$ffmpegVersion:$platform")
        "additionalRuntimeClasspath"("org.bytedeco:javacpp:$javacppVersion:$platform")
        "additionalRuntimeClasspath"("org.bytedeco:ffmpeg:$ffmpegVersion:$platform")
    }

    // Create: the Servo is a Create kinetic block (it turns a shaft).
    implementation("com.simibubi.create:create-1.21.1:$createVersion:slim") {
        isTransitive = false
    }
    implementation("net.createmod.ponder:ponder-neoforge:$ponderVersion+mc1.21.1")
    compileOnly("dev.engine-room.flywheel:flywheel-neoforge-api-1.21.1:$flywheelVersion")
    runtimeOnly("dev.engine-room.flywheel:flywheel-neoforge-1.21.1:$flywheelVersion")
    implementation("com.tterrag.registrate:Registrate:$registrateVersion")
}
