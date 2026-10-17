import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.net.URL

plugins {
    java
    id("com.gradleup.shadow") version "9.0.0"
}

group = "de.winniepat"
version = "0.0.1"
description = "SMPWrapped - Minecraft SMP Wrapped Plugin + Web Dashboard"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
    options.encoding = "UTF-8"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    // Bukkit/Spigot API 1.16.3 (oldest supported). The Bukkit API is largely
    // additive, so this single jar also runs on every server up to 26.3.
    compileOnly("org.spigotmc:spigot-api:1.16.3-R0.1-SNAPSHOT")

    // Javalin web server
    implementation("io.javalin:javalin:6.4.0")

    // HikariCP connection pool
    implementation("com.zaxxer:HikariCP:6.2.1")

    // SQLite JDBC
    implementation("org.xerial:sqlite-jdbc:3.47.2.0")

    // Jackson for JSON (Javalin uses it)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")

    // SLF4J (Paper provides the implementation)
    implementation("org.slf4j:slf4j-api:2.0.16")
    // Provider so Javalin/Jetty/Hikari logs route to java.util.logging (and thus
    // Paper's console) instead of emitting "No SLF4J providers were found".
    implementation("org.slf4j:slf4j-jdk14:2.0.16")



    // Testing
    testImplementation("org.spigotmc:spigot-api:1.16.3-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.junit.platform:junit-platform-launcher:1.11.4")
    testImplementation("org.mockito:mockito-core:5.15.2")
    testImplementation("org.assertj:assertj-core:3.27.2")
    testImplementation("com.h2database:h2:2.3.232")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("")
    archiveFileName.set("SMPWrapped-${version}.jar")

    // Merge META-INF/services entries from all dependencies (needed by Jetty's
    // ServiceLoader lookups) and drop duplicate legal/module metadata. Paper's
    // plugin remapper (NeoForge ART) refuses to load a jar that contains any
    // duplicate entries, which otherwise breaks plugin loading on 1.21+.
    mergeServiceFiles()
    exclude("META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/LICENSE.md")
    exclude("META-INF/NOTICE", "META-INF/NOTICE.txt", "META-INF/NOTICE.md")
    exclude("META-INF/DEPENDENCIES", "META-INF/DEPENDENCIES.txt")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    exclude("module-info.class")
    exclude("META-INF/versions/**/module-info.class")

    // Relocate dependencies to avoid conflicts with other plugins / the server
    relocate("io.javalin", "de.winniepat.smpwrapped.libs.javalin")
    relocate("org.eclipse.jetty", "de.winniepat.smpwrapped.libs.jetty")
    relocate("com.zaxxer.hikari", "de.winniepat.smpwrapped.libs.hikari")
    relocate("org.xerial", "de.winniepat.smpwrapped.libs.sqlite")
    relocate("com.fasterxml.jackson", "de.winniepat.smpwrapped.libs.jackson")
    relocate("org.slf4j", "de.winniepat.smpwrapped.libs.slf4j")

    relocate("kotlin", "de.winniepat.smpwrapped.libs.kotlin")
    relocate("okhttp3", "de.winniepat.smpwrapped.libs.okhttp")
    relocate("okio", "de.winniepat.smpwrapped.libs.okio")
}

tasks.processResources {
    val props = mapOf(
        "version" to version,
        "description" to project.description
    )
    inputs.property("expandVersion", version)
    inputs.property("expandDescription", project.description)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

val java21Executable: String = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}.get().executablePath.asFile.absolutePath

tasks {
    build {
        dependsOn(shadowJar)
    }

    // Custom run-server task (compatible with Gradle 9.5.1).
    // Uses Exec + `-jar` so we don't need to know Paper's main class,
    // and does not participate in the configuration cache because it
    // prepares local files and launches a long-running server process.
    register<Exec>("runServer") {
        group = "application"
        description = "Run a Paper server with SMPWrapped plugin"

        dependsOn(shadowJar)
        notCompatibleWithConfigurationCache("Prepares and launches a local Paper test server")

        val paperVersion = "1.21.11"
        val paperBuild = "132"
        val serverDir = layout.buildDirectory.dir("server").get().asFile
        val paperJar = serverDir.resolve("paper-$paperVersion.jar")
        val pluginsDir = serverDir.resolve("plugins")
        val pluginJar = layout.buildDirectory.file("libs/SMPWrapped-${version}.jar").get().asFile
        val javaExe = java21Executable

        val memory = (project.findProperty("smppwrapped.memory") as String?) ?: "2G"

        doFirst {
            serverDir.mkdirs()
            pluginsDir.mkdirs()

            if (!paperJar.exists()) {
                val fallbackUrl =
                    "https://api.papermc.io/v2/projects/paper/versions/$paperVersion/builds/$paperBuild/downloads/paper-$paperVersion-$paperBuild.jar"
                println("Resolving latest Paper $paperVersion build...")
                val downloadUrl = try {
                    @Suppress("DEPRECATION")
                    val conn = URL("https://fill.papermc.io/v3/projects/paper/versions/$paperVersion/builds/latest").openConnection()
                    conn.setRequestProperty("User-Agent", "SMPWrapped-build/1.0 (gradle)")
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000
                    val json = conn.getInputStream().bufferedReader().use { it.readText() }
                    Regex("\"url\"\\s*:\\s*\"([^\"]+\\.jar)\"").find(json)?.groupValues?.get(1) ?: fallbackUrl
                } catch (e: Exception) {
                    println("Could not resolve latest Paper build (${e.message}); using pinned build $paperBuild.")
                    fallbackUrl
                }
                println("Downloading Paper from $downloadUrl ...")
                @Suppress("DEPRECATION")
                URL(downloadUrl).openStream().use { input ->
                    paperJar.outputStream().use { output -> input.copyTo(output) }
                }
                println("Paper server JAR downloaded (${paperJar.length() / (1024 * 1024)} MB).")
            }

            if (!pluginJar.exists()) {
                throw GradleException(
                    "Plugin JAR not found at ${pluginJar.absolutePath}. Run './gradlew shadowJar' first."
                )
            }

            val targetPluginJar = pluginsDir.resolve(pluginJar.name)
            if (!targetPluginJar.exists() || targetPluginJar.lastModified() < pluginJar.lastModified()) {
                println("Copying ${pluginJar.name} to plugins/ ...")
                pluginJar.copyTo(targetPluginJar, overwrite = true)
            }

            serverDir.resolve("eula.txt").let { eulaFile ->
                if (!eulaFile.exists()) eulaFile.writeText("eula=true\n")
            }

            serverDir.resolve("server.properties").let { propsFile ->
                if (!propsFile.exists()) {
                    propsFile.writeText(
                        """
                        server-port=25565
                        online-mode=false
                        motd=SMPWrapped Test Server
                        max-players=20
                        view-distance=10
                        simulation-distance=10
                        """.trimIndent() + "\n"
                    )
                }
            }
        }

        workingDir(serverDir)
        standardInput = System.`in`
        commandLine(
            javaExe,
            "-Xms$memory", "-Xmx$memory",
            "-Dcom.mojang.eula.agree=true",
            "-jar", paperJar.name,
            "--nogui"
        )
    }
}
