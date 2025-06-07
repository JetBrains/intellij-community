/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioPathUtil;
import com.intellij.openapi.vfs.LocalFileSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

public class MavenCustomNioRepositoryHelper {

  private final Path myTempDir;
  private final Path myWorkingData;
  private final String[] mySubFolders;

  public MavenCustomNioRepositoryHelper(Path tempDir, String... subFolders) throws IOException {
    myTempDir = tempDir;
    mySubFolders = subFolders;

    myWorkingData = myTempDir.resolve("testData");
    if (!Files.exists(myWorkingData)) {
      Files.createDirectories(myWorkingData);
    }

    for (String each : mySubFolders) {
      addTestData(each);
    }
  }

  public void addTestData(String relativePath) throws IOException {
    addTestData(relativePath, relativePath);
  }

  public void addTestData(String relativePathFrom, String relativePathTo) throws IOException {
    Path to = myWorkingData.resolve(relativePathTo);
    Path from = getOriginalTestDataPath().resolve(relativePathFrom);
    Files.copy(from, to);
    LocalFileSystem.getInstance().refreshNioFiles(Collections.singleton(to));
  }

  public Path getTestData(String relativePath) {
    return myWorkingData.resolve(relativePath);
  }

  public void delete(String relativePath) {
    try {
      FileUtil.delete(getTestData(relativePath));
    }
    catch (IOException e) {
      throw new IllegalStateException("Unable to delete " + relativePath);
    }
  }

  public void copy(String fromRelativePath, String toRelativePath) throws IOException {
    Path from = getTestData(fromRelativePath);
    Path to = getTestData(toRelativePath);

    if (Files.isDirectory(from)) {
      FileUtil.copyDir(from.toFile(), to.toFile());
    }
    else {
      Files.copy(from, to);
    }

    LocalFileSystem.getInstance().refreshNioFiles(Collections.singleton(to));
  }

  public static Path getOriginalTestDataPath() {
    String sourcesDir = System.getProperty("maven.sources.dir", PluginPathManager.getPluginHomePath("maven"));
    Path originalTestDataPath = NioPathUtil.toNioPathOrNull(sourcesDir);
    return originalTestDataPath.resolve("src/test/data");
  }
}
