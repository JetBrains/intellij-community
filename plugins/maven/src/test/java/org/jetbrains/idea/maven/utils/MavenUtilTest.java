// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.server.MavenServerUtil;

import java.io.File;
import java.io.IOException;

public class MavenUtilTest extends MavenTestCase {

  public void testFindLocalRepoSchema10() throws IOException {
    VirtualFile file = createProjectSubFile("testsettings.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                                                "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\"\n" +
                                                                "          xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                                                                "          xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd\">\n" +
                                                                "  <localRepository>mytestpath</localRepository>" +
                                                                "</settings>");
    assertEquals("mytestpath", MavenUtil.getRepositoryFromSettings(new File(file.getPath())));
  }

  public void testFindLocalRepoSchema11() throws IOException {
    VirtualFile file = createProjectSubFile("testsettings.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                                                "<settings xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.1.0 http://maven.apache.org/xsd/settings-1.1.0.xsd\"\n" +
                                                                "          xmlns=\"http://maven.apache.org/SETTINGS/1.1.0\"\n" +
                                                                "          xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
                                                                "  <localRepository>mytestpath</localRepository>" +
                                                                "</settings>");
    assertEquals("mytestpath", MavenUtil.getRepositoryFromSettings(new File(file.getPath())));
  }

  public void testFindLocalRepoWithoutXmls() throws IOException {
    VirtualFile file = createProjectSubFile("testsettings.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                                                "<settings>" +
                                                                "  <localRepository>mytestpath</localRepository>" +
                                                                "</settings>");
    assertEquals("mytestpath", MavenUtil.getRepositoryFromSettings(new File(file.getPath())));
  }

  public void testFindLocalRepoWithNonTrimmed() throws IOException {
    VirtualFile file = createProjectSubFile("testsettings.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                                                "<settings>" +
                                                                "  <localRepository>\n\t" +
                                                                "     \tmytestpath\n" +
                                                                "   \t</localRepository>" +
                                                                "</settings>");
    assertEquals("mytestpath", MavenUtil.getRepositoryFromSettings(new File(file.getPath())));
  }

  public void testSystemProperties() throws IOException {

    try {
      MavenServerUtil.addProperty("testSystemPropertiesRepoPath", "test");
      VirtualFile file = createProjectSubFile("testsettings.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                                                  "<settings>" +
                                                                  "  <localRepository>${testSystemPropertiesRepoPath}/testpath</localRepository>" +
                                                                  "</settings>");
      assertEquals("test/testpath", MavenUtil.getRepositoryFromSettings(new File(file.getPath())));
    } finally {
      MavenServerUtil.removeProperty("testSystemPropertiesRepoPath");
    }
  }
}