// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.jarRepository.RemoteRepositoriesConfiguration;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class RepositoriesImportingTest extends MavenMultiVersionImportingTestCase {


  @Test
  public void testMirrorCentralImport() throws IOException {
    String oldSettingsFile = getMavenGeneralSettings().getUserSettingsFile();
    try {
      VirtualFile settingsXml = createProjectSubFile("settings.xml", "<settings>" +
                                                                     "<mirrors>" +
                                                                     "    <mirror>" +
                                                                     "      <id>central-mirror</id>" +
                                                                     "      <name>mirror</name>" +
                                                                     "      <url>https://example.com/maven2</url>" +
                                                                     "      <mirrorOf>central</mirrorOf>" +
                                                                     "    </mirror>" +
                                                                     "  </mirrors>" +
                                                                     "</settings>");
      getMavenGeneralSettings().setUserSettingsFile(settingsXml.getCanonicalPath());

      importProject("<groupId>test</groupId>" +
                    "<artifactId>project</artifactId>" +
                    "<packaging>pom</packaging>" +
                    "<version>1</version>");

      assertHaveRepositories("https://example.com/maven2");
    }
    finally {
      getMavenGeneralSettings().setUserSettingsFile(oldSettingsFile);
    }
  }

  @Test
  public void testMirrorAllImport() throws IOException {
    String oldSettingsFile = getMavenGeneralSettings().getUserSettingsFile();
    try {
      VirtualFile settingsXml = createProjectSubFile("settings.xml", "<settings>" +
                                                                     "<mirrors>" +
                                                                     "    <mirror>" +
                                                                     "      <id>central-mirror</id>" +
                                                                     "      <name>mirror</name>" +
                                                                     "      <url>https://example.com/maven2</url>" +
                                                                     "      <mirrorOf>*</mirrorOf>" +
                                                                     "    </mirror>" +
                                                                     "  </mirrors>" +
                                                                     "</settings>");
      getMavenGeneralSettings().setUserSettingsFile(settingsXml.getCanonicalPath());

      importProject("<groupId>test</groupId>" +
                    "<artifactId>project</artifactId>" +
                    "<packaging>pom</packaging>" +
                    "<version>1</version>");

      assertHaveRepositories("https://example.com/maven2");
    }
    finally {
      getMavenGeneralSettings().setUserSettingsFile(oldSettingsFile);
    }
  }

  @Test
  public void testMirrorAllExceptCentralImport() throws IOException {
    String oldSettingsFile = getMavenGeneralSettings().getUserSettingsFile();
    try {
      VirtualFile settingsXml = createProjectSubFile("settings.xml", "<settings>" +
                                                                     "<mirrors>" +
                                                                     "    <mirror>" +
                                                                     "      <id>central-mirror</id>" +
                                                                     "      <name>mirror</name>" +
                                                                     "      <url>https://example.com/maven2</url>" +
                                                                     "      <mirrorOf>*,!central</mirrorOf>" +
                                                                     "    </mirror>" +
                                                                     "  </mirrors>" +
                                                                     "</settings>");
      getMavenGeneralSettings().setUserSettingsFile(settingsXml.getCanonicalPath());

      importProject("<groupId>test</groupId>" +
                    "<artifactId>project</artifactId>" +
                    "<packaging>pom</packaging>" +
                    "<version>1</version>");

      assertHaveRepositories("https://repo1.maven.org/maven2");
      assertDoNotHaveRepositories("https://example.com/maven2<");
    }
    finally {
      getMavenGeneralSettings().setUserSettingsFile(oldSettingsFile);
    }
  }

  private void assertDoNotHaveRepositories(String... repos) {
    List<String> actual = ContainerUtil.map(RemoteRepositoriesConfiguration.getInstance(myProject).getRepositories(), it -> it.getUrl());

    assertDoNotContain(actual, repos);
  }


  private void assertHaveRepositories(String... repos) {
    List<String> actual = ContainerUtil.map(RemoteRepositoriesConfiguration.getInstance(myProject).getRepositories(), it -> it.getUrl());

    assertContain(actual, repos);
  }
}
