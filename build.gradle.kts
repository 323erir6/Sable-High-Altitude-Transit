plugins {
    java
    id("net.neoforged.moddev") version "2.0.80"
}

version = property("mod_version")!!
group = "dev.sablehighaltitudetransit"

base {
    archivesName.set("sable-high-altitude-transit")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
}

neoForge {
    version = property("neo_version") as String
    mods {
        create("sable_high_altitude_transit") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    compileOnly(files("../_deps_sable_2_0_5/sable-neoforge-1.21.1-2.0.5.jar"))
    compileOnly(files("../_deps_sable/META-INF/jarjar/sable-companion-common-1.21.1-1.6.0.jar"))
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version)
    }
}
