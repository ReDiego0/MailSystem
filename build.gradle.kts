plugins {
    kotlin("jvm") version "2.2.21"
    id("com.gradleup.shadow") version "9.2.2"
}

group = "org.ReDiego0"
version = "1.0.0"

layout.buildDirectory.set(file("${System.getProperty("user.home")}/.gradle/build-mailsystem"))

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") {
        name = "placeholderapi"
    }
    maven("https://repo.luckperms.net/releases/") {
        name = "luckperms"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.167-beta")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    compileOnly("com.mysql:mysql-connector-j:9.1.0")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.luckperms:api:5.4")
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<JavaCompile> {
    options.release.set(25)
    options.encoding = "UTF-8"
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    relocate("com.zaxxer.hikari", "org.ReDiego0.mailSystem.libs.hikari")
    relocate("org.sqlite", "org.ReDiego0.mailSystem.libs.sqlite")
}

tasks.build {
    dependsOn(tasks.shadowJar)
    doLast {
        val outDir = file("${projectDir}/out")
        outDir.mkdirs()
        val jarFile = tasks.shadowJar.get().archiveFile.get().asFile
        if (jarFile.exists()) {
            val target = File(outDir, "${project.name}-${project.version}.jar")
            jarFile.copyTo(target, overwrite = true)
            println("Jar copiado exitosamente a: ${target.absolutePath}")
        }
    }
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
