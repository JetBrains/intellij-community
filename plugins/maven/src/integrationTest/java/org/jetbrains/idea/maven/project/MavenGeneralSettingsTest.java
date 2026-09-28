// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.maven.execution.MavenExecutionOptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class MavenGeneralSettingsTest {
  private static final TestFixture<Path> tempDir = tempPathFixture();
  private static final TestFixture<Project> project = projectFixture(tempDir, OpenProjectTask.build(), true);

  @Test
  public void testUpdateFromMavenConfigDisabled() throws IOException {
    Path dir = Files.createTempDirectory(tempDir.get(), "mavenConfig");
    writeFile(dir, ".mvn/maven.config", "-o -U -N -T3 -q -X -e -C -ff -s user-settings.xml -gs global-settings.xml");
    VirtualFile virtualFile = findFile(dir.resolve(".mvn"));

    MavenGeneralSettings settings = new MavenGeneralSettings(project.get());
    settings.updateFromMavenConfig(Collections.singletonList(virtualFile));

    assertEquals(MavenExecutionOptions.ChecksumPolicy.NOT_SET, settings.getChecksumPolicy());
    assertEquals(MavenExecutionOptions.FailureMode.NOT_SET, settings.getFailureBehavior());
    assertEquals(MavenExecutionOptions.LoggingLevel.INFO, settings.getOutputLevel());
    assertFalse(settings.isAlwaysUpdateSnapshots());
    assertFalse(settings.isWorkOffline());
    assertFalse(settings.isPrintErrorStackTraces());
    assertFalse(settings.isNonRecursive());
    assertTrue(StringUtil.isEmpty(settings.getThreads()));
  }

  @Test
  public void testUpdateFromMavenConfig() throws IOException {
    Path dir = Files.createTempDirectory(tempDir.get(), "mavenConfig");
    Path userSettings = writeFile(dir, "user-settings.xml", "<settings/>");
    writeFile(dir, "global-settings.xml", "<settings/>");
    writeFile(dir, ".mvn/maven.config", "-o -U -N -T3 -q -X -e -C -ff -s user-settings.xml -gs global-settings.xml");
    VirtualFile virtualFile = findFile(userSettings);

    MavenGeneralSettings settings = new MavenGeneralSettings(project.get());
    settings.setUseMavenConfig(true);
    settings.updateFromMavenConfig(Collections.singletonList(virtualFile));
    assertEquals(MavenExecutionOptions.ChecksumPolicy.FAIL, settings.getChecksumPolicy());
    assertEquals(MavenExecutionOptions.FailureMode.FAST, settings.getFailureBehavior());
    assertEquals(MavenExecutionOptions.LoggingLevel.DISABLED, settings.getOutputLevel());
    assertTrue(settings.isAlwaysUpdateSnapshots());
    assertTrue(settings.isWorkOffline());
    assertTrue(settings.isPrintErrorStackTraces());
    assertTrue(settings.isNonRecursive());
    assertEquals("3", settings.getThreads());
  }

  private static Path writeFile(Path dir, String relativePath, String content) throws IOException {
    Path file = dir.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
    return file;
  }

  private static VirtualFile findFile(Path path) {
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
    assertNotNull(file, "File not found in VFS: " + path);
    return file;
  }
}
