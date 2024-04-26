// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.jps.maven.model.impl.MavenProjectConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public abstract class ReadConfigFilesTestCase extends UsefulTestCase {
  public void testSimpleProperties() throws IOException {
    doTestBothConfigFiles("-DmyProperty=value", Map.of("myProperty", "value"));
    doTestBothConfigFiles("-Da=b -Dc=d\n-De=f", Map.of("a", "b", "c", "d", "e", "f"));
  }

  public void testPropertiesWithoutValue() throws IOException {
    doTestJvmConfig("-DmyProperty", Map.of("myProperty", ""));
    doTestMavenConfig("-DmyProperty", Map.of("myProperty", "true"));
    doTestJvmConfig("-Da -Dc=d\n-De", Map.of("a", "", "c", "d", "e", ""));
    doTestMavenConfig("-Da -Dc=d\n-De", Map.of("a", "true", "c", "d", "e", "true"));
  }

  public void testSpacesInValues() throws IOException {
    doTestJvmConfig("\"-DmyProperty=long value\"", Map.of("myProperty", "long value"));
    doTestJvmConfig("-Da=b \"-DmyProperty=long value\"\n-Dc=d", Map.of("myProperty", "long value", "a", "b", "c", "d"));
    //spaces in properties in maven.config aren't handled properly anyway, it just splits the whole content by spaces and feeds GnuParser with it, see org.apache.maven.cli.MavenCli#cli
  }

  private void doTestBothConfigFiles(String text, Map<String, String> expected) throws IOException {
    doTestJvmConfig(text, expected);
    doTestMavenConfig(text, expected);
  }

  private void doTestMavenConfig(String text, Map<String, String> expected) throws IOException {
    doTestConfigFile(text, expected, MavenConstants.MAVEN_CONFIG_RELATIVE_PATH);
  }

  private void doTestJvmConfig(String text, Map<String, String> expected) throws IOException {
    doTestConfigFile(text, expected, MavenConstants.JVM_CONFIG_RELATIVE_PATH);
  }

  private void doTestConfigFile(String text, Map<String, String> expected, final String relativePath) throws IOException {
    File baseDir = FileUtil.createTempDirectory("mavenServerConfig", null);
    FileUtil.writeToFile(new File(baseDir, relativePath), text);
    Map<String, String> result = readProperties(baseDir);
    assertEquals(expected, result);
  }

  @NotNull
  protected abstract Map<String, String> readProperties(File baseDir);

  public static class ReadConfigFilesInEmbedderTest extends ReadConfigFilesTestCase {
    @Override
    @NotNull
    protected Map<String, String> readProperties(File baseDir) {
      Map<String, String> result = new HashMap<>();
      MavenServerConfigUtil.readConfigFiles(baseDir, result);
      return result;
    }
  }

  public static class ReadConfigFilesInMavenConfigurationTest extends ReadConfigFilesTestCase {
    @Override
    @NotNull
    protected Map<String, String> readProperties(File baseDir) {
      return MavenProjectConfiguration.readConfigFiles(baseDir);
    }
  }

  public static class ReadConfigFilesInMavenProjectTest extends ReadConfigFilesTestCase {
    @Override
    @NotNull
    protected Map<String, String> readProperties(File baseDir) {
      Map<String, String> result = new HashMap<>();
      result.putAll(MavenProject.readConfigFile(baseDir, MavenProject.ConfigFileKind.MAVEN_CONFIG));
      result.putAll(MavenProject.readConfigFile(baseDir, MavenProject.ConfigFileKind.JVM_CONFIG));
      return result;
    }
  }
}
