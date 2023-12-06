plugins {
  java
}

repositories {
  mavenCentral()
}

sourceSets.main {
  java.setSrcDirs(listOf("src"))
  resources.setSrcDirs(listOf("resources"))
}
sourceSets.test {
  java.setSrcDirs(listOf("testSrc"))
}

dependencies {
  implementation("org.jetbrains:annotations:24.0.0")
  implementation("net.java.dev.jna:jna-platform:5.13.0")
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
  testImplementation("org.assertj:assertj-core:3.24.2")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
}

tasks.test {
  useJUnitPlatform()
}
