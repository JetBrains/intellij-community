// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenTestCase;

import java.io.File;
import java.io.IOException;

public class MavenUtilTest extends MavenTestCase {

  public void testFindLocalRepo() throws IOException {
    VirtualFile file = createProjectSubFile("testsettings.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                                                "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\"\n" +
                                                                "          xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                                                                "          xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd\">\n" +
                                                                "  <localRepository>mytestpath</localRepository>" +
                                                                "</settings>");
    assertEquals("mytestpath", MavenUtil.getRepositoryFromSettings(new File(file.getPath())));
  }
}