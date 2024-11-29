// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.maven.testFramework.MavenTestCase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.server.MavenServerUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class MavenUtilTest extends MavenTestCase {

  public void testFindExtension() throws IOException {
    VirtualFile file = createProjectSubFile(".mvn/extensions.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <extensions>
          <extension>
              <groupId>group-id</groupId>
              <artifactId>artifact-id</artifactId>
              <version>1.0.42</version>
          </extension>
      </extensions>
      """);
    assertTrue(MavenUtil.containsDeclaredExtension(file.toNioPath(), new MavenId("group-id:artifact-id:1.0.42")));
  }

  public void testFindLocalRepoSchema12() throws IOException {
    VirtualFile file = createProjectSubFile("testsettings.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
        <localRepository>mytestpath</localRepository></settings>""");
    assertEquals("mytestpath", MavenUtil.getRepositoryFromSettings(file.toNioPath()));
  }

  public void testFindLocalRepoSchema10() throws IOException {
    VirtualFile file = createProjectSubFile("testsettings.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
        <localRepository>mytestpath</localRepository></settings>""");
    assertEquals("mytestpath", MavenUtil.getRepositoryFromSettings(file.toNioPath()));
  }

  public void testFindLocalRepoSchema11() throws IOException {
    VirtualFile file = createProjectSubFile("testsettings.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <settings xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.1.0 http://maven.apache.org/xsd/settings-1.1.0.xsd"
                xmlns="http://maven.apache.org/SETTINGS/1.1.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">  <localRepository>mytestpath</localRepository></settings>""");
    assertEquals("mytestpath", MavenUtil.getRepositoryFromSettings(file.toNioPath()));
  }

  public void testFindLocalRepoWithoutXmls() throws IOException {
    VirtualFile file = createProjectSubFile("testsettings.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <settings>
        <localRepository>mytestpath</localRepository>
      </settings>
      """);
    assertEquals("mytestpath", MavenUtil.getRepositoryFromSettings(file.toNioPath()));
  }

  public void testFindLocalRepoWithNonTrimmed() throws IOException {
    VirtualFile file = createProjectSubFile("testsettings.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <settings>  <localRepository>
      \t     \tmytestpath
         \t</localRepository></settings>""");
    assertEquals("mytestpath", MavenUtil.getRepositoryFromSettings(file.toNioPath()));
  }

  public void testSystemProperties() throws IOException {

    try {
      MavenServerUtil.addProperty("testSystemPropertiesRepoPath", "test");
      VirtualFile file = createProjectSubFile("testsettings.xml", """
        <?xml version="1.0" encoding="UTF-8"?>
        <settings>
          <localRepository>${testSystemPropertiesRepoPath}/testpath</localRepository>
        </settings>
        """);
      assertEquals("test/testpath", MavenUtil.getRepositoryFromSettings(file.toNioPath()));
    } finally {
      MavenServerUtil.removeProperty("testSystemPropertiesRepoPath");
    }
  }

  public void testGetRepositoryFromSettingsWithBadSymbols() throws IOException {
    VirtualFile file = createProjectSubFile("testsettings.xml");
    String str = """
      <settings> <!-- Bad UTF-8 symbol: ü -->
        <localRepository>mytestpath</localRepository>
      </settings>""";
    Files.writeString(file.toNioPath(), str, StandardCharsets.ISO_8859_1);
    assertEquals("mytestpath", MavenUtil.getRepositoryFromSettings(file.toNioPath()));
  }
}