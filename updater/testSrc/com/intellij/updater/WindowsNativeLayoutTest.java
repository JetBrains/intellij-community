// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WindowsNativeLayoutTest {
  private static final int JAVA_11_CLASS_VERSION = 55;
  private static final int JAVA_22_CLASS_VERSION = 66;

  @Test
  void baseWindowsNativeClassTargetsJava11() throws IOException {
    assertThat(classVersion(WindowsNative.class)).isEqualTo(JAVA_11_CLASS_VERSION);
  }

  @Test
  void updaterEntrypointTargetsJava11() throws IOException {
    assertThat(classVersion(Runner.class)).isEqualTo(JAVA_11_CLASS_VERSION);
  }

  @Test
  void fatJarContainsJava22WindowsNativeOverride() throws IOException {
    var fatJar = System.getProperty("updater.fat.jar");
    assumeTrue(fatJar != null, "The Gradle fatJar task did not provide the artifact path");

    try (var jar = new JarFile(Path.of(fatJar).toFile())) {
      assertThat(jar.getManifest().getMainAttributes().getValue("Multi-Release")).isEqualTo("true");
      assertThat(classVersion(jar, "com/intellij/updater/WindowsNative.class")).isEqualTo(JAVA_11_CLASS_VERSION);
      assertThat(classVersion(jar, "META-INF/versions/22/com/intellij/updater/WindowsNative.class"))
        .isEqualTo(JAVA_22_CLASS_VERSION);
    }
  }

  @Test
  void baseWindowsNativeDoesNotLoadNativeLibraries() {
    assertThat(WindowsNative.supplier().get()).isNull();
  }

  @Test
  void minFeatureIsTwentyTwo() {
    assertThat(WindowsNative.MIN_FEATURE).isEqualTo(22);
  }

  private static int classVersion(Class<?> aClass) throws IOException {
    var resource = '/' + aClass.getName().replace('.', '/') + ".class";
    try (var stream = aClass.getResourceAsStream(resource)) {
      assertThat(stream).isNotNull();
      return classVersion(stream);
    }
  }

  private static int classVersion(JarFile jar, String name) throws IOException {
    var entry = jar.getJarEntry(name);
    assertThat(entry).isNotNull();
    try (var stream = jar.getInputStream(entry)) {
      return classVersion(stream);
    }
  }

  private static int classVersion(InputStream stream) throws IOException {
    try (var input = new DataInputStream(stream)) {
      assertThat(input.readInt()).isEqualTo(0xCAFEBABE);
      input.readUnsignedShort();
      return input.readUnsignedShort();
    }
  }
}
