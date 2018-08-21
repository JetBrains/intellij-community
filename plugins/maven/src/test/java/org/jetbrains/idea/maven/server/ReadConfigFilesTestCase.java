// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.jps.maven.model.impl.MavenProjectConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public abstract class ReadConfigFilesTestCase extends UsefulTestCase {
  public void testSimpleProperties() throws IOException {
    doTestBothConfigFiles("-DmyProperty=value", ContainerUtil.stringMap("myProperty", "value"));
    doTestBothConfigFiles("-Da=b -Dc=d\n-De=f", ContainerUtil.stringMap("a", "b", "c", "d", "e", "f"));
  }

  public void testSpacesInValues() throws IOException {
    doTestJvmConfig("\"-DmyProperty=long value\"", ContainerUtil.stringMap("myProperty", "long value"));
    doTestJvmConfig("-Da=b \"-DmyProperty=long value\"\n-Dc=d", ContainerUtil.stringMap("myProperty", "long value", "a", "b", "c", "d"));
    //spaces in properties in maven.config aren't handled properly anyway, it just splits the whole content by spaces and feeds GnuParser with it, see org.apache.maven.cli.MavenCli#cli
  }

  private void doTestBothConfigFiles(final String text, final Map<String, String> expected) throws IOException {
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
    //noinspection MisorderedAssertEqualsArguments
    assertEquals(expected, result);
  }

  @NotNull
  protected abstract Map<String, String> readProperties(File baseDir);

  public static class ReadConfigFilesInEmbedderTest extends ReadConfigFilesTestCase {
    @Override
    @NotNull
    protected Map<String, String> readProperties(File baseDir) {
      Map<String, String> result = new HashMap<>();
      Maven3ServerEmbedder.readConfigFiles(baseDir, result);
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
      result.putAll(MavenProject.readConfigFile(baseDir, MavenConstants.MAVEN_CONFIG_RELATIVE_PATH));
      result.putAll(MavenProject.readConfigFile(baseDir, MavenConstants.JVM_CONFIG_RELATIVE_PATH));
      return result;
    }
  }
}
