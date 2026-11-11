// Patched versions of libraries the Android Gradle Plugin uses while building.
// None of these are packaged into the app. They clear GitHub security alerts
// for the build tooling. Remove an entry once a newer Android Gradle Plugin
// brings that version (or later) by itself; check with ./gradlew buildEnvironment.
buildscript {
    dependencies {
        constraints {
            classpath("org.bouncycastle:bcprov-jdk18on:1.85")
            classpath("org.bouncycastle:bcpkix-jdk18on:1.86")
            classpath("org.bouncycastle:bcutil-jdk18on:1.85")
            classpath("com.google.guava:guava:33.7.2-jre")
            classpath("org.bitbucket.b_c:jose4j:0.9.6")
            classpath("org.jdom:jdom2:2.0.6.1")
            classpath("org.apache.commons:commons-lang3:3.18.0")
            classpath("org.apache.httpcomponents:httpclient:4.5.14")
        }
    }
}

plugins {
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

// Android Lint runs from its own classpath, so it needs the same pins.
// Keep this list in step with the buildscript block above.
subprojects {
    configurations.matching { it.name == "androidLintTool" }.configureEach {
        resolutionStrategy.force(
            "org.bouncycastle:bcprov-jdk18on:1.85",
            "org.bouncycastle:bcpkix-jdk18on:1.86",
            "org.bouncycastle:bcutil-jdk18on:1.85",
            "com.google.guava:guava:33.7.2-jre",
            "org.apache.commons:commons-lang3:3.18.0",
            "org.apache.httpcomponents:httpclient:4.5.14",
        )
    }
}
