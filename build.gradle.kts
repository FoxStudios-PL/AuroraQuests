import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import xyz.jpenilla.runtask.task.AbstractRun
import groovy.util.Node
import groovy.util.NodeList
import java.net.URI
import java.util.*

fun loadProperties(filename: String): Properties {
    val properties = Properties()
    if (!file(filename).exists()) {
        return properties
    }
    file(filename).inputStream().use { properties.load(it) }
    return properties
}

plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.2"
    id("maven-publish")
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "gg.auroramc"
// Suffixed so the 26.2 artifact is never mistaken for the 1.21.11 one built from main.
version = "2.2.1-26.2"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

repositories {
    flatDir {
        dirs("libs")
    }
    mavenCentral()
    mavenLocal()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.auroramc.gg/releases/")
    maven("https://repo.auroramc.gg/snapshots/")
    maven("https://repo.aikar.co/content/groups/aikar/")
    maven("https://mvn.lumine.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://maven.citizensnpcs.co/repo")
    maven("https://jitpack.io/")
    //maven("https://repo.projectshard.dev/repository/releases/")
    maven("https://repo.oraxen.com/releases")
    maven("https://nexus.phoenixdevt.fr/repository/maven-public/")
    maven("https://repo.fancyinnovations.com/releases")
    maven("https://repo.tabooproject.org/repository/releases/")
    maven("https://repo.nexomc.com/releases/")
    maven("https://repo.nightexpressdev.com/releases")
    maven("https://repo.pyr.lol/snapshots")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.31-alpha")
    compileOnly("gg.auroramc:Aurora:2.6.0-SNAPSHOT")
    compileOnly("gg.auroramc:AuroraLevels:1.6.2")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("dev.aurelium:auraskills-api-bukkit:2.2.0")
    compileOnly("io.lumine:Mythic-Dist:5.6.1")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.7")
    compileOnly("net.citizensnpcs:citizens-main:2.0.33-SNAPSHOT") {
        exclude(group = "*", module = "*")
    }
    compileOnly("com.github.Xiao-MoMi:Custom-Fishing:2.3.3")
    //compileOnly("com.nisovin.shopkeepers:ShopkeepersAPI:2.23.3")
    compileOnly("com.github.Gypopo:EconomyShopGUI-API:1.10.1")
    compileOnly("io.th0rgal:oraxen:1.179.0")
    compileOnly("com.github.brcdev-minecraft:shopgui-api:3.0.0") {
        exclude(group = "org.spigotmc", module = "spigot-api")
    }
    compileOnly("io.lumine:MythicLib-dist:1.6.2-SNAPSHOT")
    // Gradle 9 no longer resolves flatDir coordinates declared with name/group/version,
    // so the jars dropped in libs/ are referenced by path instead.
    compileOnly(files("libs/MythicDungeons-2.0.0-SNAPSHOT.jar"))
    compileOnly(files("libs/znpcs-5.0.jar"))
    compileOnly(files("libs/Shopkeepers-2.23.3.jar"))
    compileOnly(files("libs/SuperiorSkyblock2-2025.1.jar"))
    //compileOnly("com.bgsoftware:SuperiorSkyblockAPI:2025.1")
    compileOnly(files("libs/EpicCraftingsPlus-7.36.2.jar"))
    compileOnly("lol.pyr:znpcsplus-api:2.1.0-SNAPSHOT")
    compileOnly("de.oliver:FancyNpcs:2.6.0")
    compileOnly("ink.ptms.adyeshach:all:2.0.0-snapshot-1")
    compileOnly("com.nexomc:nexo:1.8.0")
    compileOnly(files("libs/LuxRealms-1.2.4.jar"))
    compileOnly(files("libs/FoxSkills-1.2.0.jar"))
    compileOnly("su.nightexpress.excellentshop:api:5.1.2") {
        isTransitive = false
    }
    compileOnly("su.nightexpress.nightcore:main:2.16.2") {
        isTransitive = false
    }

    // BetterHud (optional, softdepend): used only to hide the quest sidebar while a
    // popup is showing. Pinned to the server version (1.14.1). The public API types
    // (BetterHudAPI, PlayerManager, HudPlayer, popup classes) live in the
    // standard-api module; HudPlayer extends BetterCommandSource, so BetterCommand
    // must be on the compile classpath too. Compile-only: never shaded, provided by
    // BetterHud at runtime and absent otherwise.
    compileOnly("io.github.toxicity188:BetterHud-standard-api:1.14.1")
    compileOnly("io.github.toxicity188:BetterCommand:1.4.3")

    implementation("co.aikar:acf-paper:0.5.1-SNAPSHOT")
    implementation("org.bstats:bstats-bukkit:3.0.2")

    compileOnly("org.quartz-scheduler:quartz:2.3.2")
    compileOnly("com.cronutils:cron-utils:9.2.0")

    testImplementation("io.papermc.paper:paper-api:26.2.build.31-alpha")
    // Only for the config-merge tests: ItemConfig is a plain POJO, no server needed.
    testImplementation("gg.auroramc:Aurora:2.6.0-SNAPSHOT") {
        isTransitive = false
    }
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.0")

    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<ShadowJar> {
    archiveFileName.set("AuroraQuests-${project.version}.jar")

    manifest {
        attributes["paperweight-mappings-namespace"] = "mojang"
    }

    relocate("co.aikar.commands", "gg.auroramc.quests.libs.acf")
    relocate("co.aikar.locales", "gg.auroramc.quests.libs.locales")
    relocate("org.bstats", "gg.auroramc.quests.libs.bstats")

    exclude("acf-*.properties")
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

// No Folia build for 26.2 yet; re-enable once one exists.
//runPaper.folia.registerTask()

tasks {
    build {
        dependsOn(shadowJar)
    }
    runServer {
        // AuroraLib is no longer auto-downloaded: the 26.2 build (2.6.0+) is not on
        // Modrinth yet, drop it into run/plugins/ manually alongside the other hooks.
        minecraftVersion("26.2")
    }
}

val publishing = loadProperties("publish.properties")

publishing {
    repositories {
        maven {
            name = "AuroraMC"
            url = if (version.toString().endsWith("SNAPSHOT")) {
                URI.create("https://repo.auroramc.gg/snapshots/")
            } else {
                URI.create("https://repo.auroramc.gg/releases/")
            }
            credentials {
                username = publishing.getProperty("username")
                password = publishing.getProperty("password")
            }
        }
    }

    publications.create<MavenPublication>("mavenJava") {
        groupId = "gg.auroramc"
        artifactId = "AuroraQuests"
        version = project.version.toString()

        from(components["java"])

        pom.withXml {
            val dependency = (asNode().get("dependencies") as NodeList).first() as Node
            (dependency.get("dependency") as NodeList).forEach {
                val node = it as Node
                val artifactIdList = node.get("artifactId") as NodeList
                val artifactId = (artifactIdList.first() as Node).text()
                if (artifactId in listOf("acf-paper")) {
                    assert(it.parent().remove(it))
                }
            }
        }
    }
}

tasks.withType<AbstractRun>().configureEach {
//    javaLauncher = javaToolchains.launcherFor {
//        vendor.set(JvmVendorSpec.JETBRAINS)
//        languageVersion.set(JavaLanguageVersion.of(25))
//    }
    jvmArgs(
        // "-XX:+AllowEnhancedClassRedefinition", //
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005" // Enable remote debugging
    )
}