// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.jps.maven.model.impl.MavenProjectConfiguration;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks that the three readers of {@code .mvn/maven.config} and {@code .mvn/jvm.config} return the same properties.
 */
public class ReadConfigFilesTest {
  @TempDir
  Path myTempDir;

  static Stream<Arguments> readers() {
    return Stream.of(
      Arguments.of(Named.<Function<Path, Map<String, String>>>of("embedder", ReadConfigFilesTest::readInEmbedder)),
      Arguments.of(Named.<Function<Path, Map<String, String>>>of("maven configuration", ReadConfigFilesTest::readInMavenConfiguration)),
      Arguments.of(Named.<Function<Path, Map<String, String>>>of("maven project", ReadConfigFilesTest::readInMavenProject))
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("readers")
  public void testSimpleProperties(Function<Path, Map<String, String>> reader) throws IOException {
    doTestBothConfigFiles(reader, "-DmyProperty=value", Map.of("myProperty", "value"));
    doTestBothConfigFiles(reader, "-Da=b -Dc=d\n-De=f", Map.of("a", "b", "c", "d", "e", "f"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("readers")
  public void testPropertiesWithoutValue(Function<Path, Map<String, String>> reader) throws IOException {
    doTestJvmConfig(reader, "-DmyProperty", Map.of("myProperty", ""));
    doTestMavenConfig(reader, "-DmyProperty", Map.of("myProperty", "true"));
    doTestJvmConfig(reader, "-Da -Dc=d\n-De", Map.of("a", "", "c", "d", "e", ""));
    doTestMavenConfig(reader, "-Da -Dc=d\n-De", Map.of("a", "true", "c", "d", "e", "true"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("readers")
  public void testSpacesInValues(Function<Path, Map<String, String>> reader) throws IOException {
    doTestJvmConfig(reader, "\"-DmyProperty=long value\"", Map.of("myProperty", "long value"));
    doTestJvmConfig(reader, "-Da=b \"-DmyProperty=long value\"\n-Dc=d", Map.of("myProperty", "long value", "a", "b", "c", "d"));
    //spaces in properties in maven.config aren't handled properly anyway, it just splits the whole content by spaces and feeds GnuParser with it, see org.apache.maven.cli.MavenCli#cli
  }

  private void doTestBothConfigFiles(Function<Path, Map<String, String>> reader, String text, Map<String, String> expected) throws IOException {
    doTestJvmConfig(reader, text, expected);
    doTestMavenConfig(reader, text, expected);
  }

  private void doTestMavenConfig(Function<Path, Map<String, String>> reader, String text, Map<String, String> expected) throws IOException {
    doTestConfigFile(reader, text, expected, MavenConstants.MAVEN_CONFIG_RELATIVE_PATH);
  }

  private void doTestJvmConfig(Function<Path, Map<String, String>> reader, String text, Map<String, String> expected) throws IOException {
    doTestConfigFile(reader, text, expected, MavenConstants.JVM_CONFIG_RELATIVE_PATH);
  }

  private void doTestConfigFile(Function<Path, Map<String, String>> reader, String text, Map<String, String> expected, String relativePath)
    throws IOException {
    Path baseDir = Files.createTempDirectory(myTempDir, "mavenServerConfig");
    Path configFile = baseDir.resolve(relativePath);
    Files.createDirectories(configFile.getParent());
    Files.writeString(configFile, text);
    assertEquals(expected, reader.apply(baseDir));
  }

  private static Map<String, String> readInEmbedder(Path baseDir) {
    Map<String, String> result = new HashMap<>();
    MavenServerConfigUtil.readConfigFiles(baseDir, result);
    return result;
  }

  private static Map<String, String> readInMavenConfiguration(Path baseDir) {
    return MavenProjectConfiguration.readConfigFiles(baseDir.toFile());
  }

  private static Map<String, String> readInMavenProject(Path baseDir) {
    Map<String, String> result = new HashMap<>();
    result.putAll(MavenProject.readConfigFile(baseDir, MavenProject.ConfigFileKind.MAVEN_CONFIG));
    result.putAll(MavenProject.readConfigFile(baseDir, MavenProject.ConfigFileKind.JVM_CONFIG));
    return result;
  }
}
