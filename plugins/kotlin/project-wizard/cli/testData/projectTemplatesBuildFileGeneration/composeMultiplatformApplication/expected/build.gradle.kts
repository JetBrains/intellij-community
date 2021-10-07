buildscript {
    repositories {
        gradlePluginPortal()
        jcenter()
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:KOTLIN_VERSION")
        classpath("com.android.tools.build:gradle:4.0.2")
    }
}

group = "me.user"
version = "1.0"

allprojects {
    repositories {
        jcenter()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
