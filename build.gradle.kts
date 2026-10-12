plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

group = "net.viraxis"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly(files("../MassiveCore/build/classes/java/main"))
    compileOnly(files("../MassiveCore/build/libs/MassiveCore-MC-1.21.4-VIRAXIS.jar"))
    compileOnly(files("../stacker/libs/worldguard.jar"))
    compileOnly(files("../stacker/libs/FastAsyncWorldEdit.jar"))
    compileOnly(files("../holograms/build/classes/java/main"))
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
tasks.compileJava { options.release.set(21); options.encoding = "UTF-8" }
tasks.jar { archiveClassifier.set("original") }
tasks.shadowJar { archiveClassifier.set("") }
tasks.build { dependsOn(tasks.shadowJar) }
