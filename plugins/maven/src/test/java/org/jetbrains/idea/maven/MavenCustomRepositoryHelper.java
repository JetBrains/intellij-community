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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileSystem;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

public class MavenCustomRepositoryHelper {
  private final File myTempDir;
  private final File myWorkingData;
  private final String[] mySubFolders;

  public MavenCustomRepositoryHelper(File tempDir, String... subFolders) throws IOException {
    myTempDir = tempDir;
    mySubFolders = subFolders;

    myWorkingData = new File(myTempDir, "testData");

    for (String each : mySubFolders) {
      addTestData(each);
    }
  }

  public void addTestData(String relativePath) throws IOException {
    File to = new File(myWorkingData, relativePath);
    FileUtil.copyDir(new File(getOriginalTestDataPath(), relativePath), to);
    LocalFileSystem.getInstance().refreshIoFiles(Collections.singleton(to));
  }

  private String getOriginalTestDataPath() {
    String sourcesDir = System.getProperty("maven.sources.dir", PluginPathManager.getPluginHomePath("maven"));
    return FileUtil.toSystemIndependentName(sourcesDir + "/src/test/data");
  }

  public String getTestDataPath(String relativePath) {
    String path = getTestData(relativePath).getPath();
    return FileUtil.toSystemIndependentName(path);
  }

  public File getTestData(String relativePath) {
    return new File(myWorkingData, relativePath);
  }

  public void delete(String relativePath) {
    FileUtil.delete(new File(getTestDataPath(relativePath)));
  }

  public void copy(String fromRelativePath, String toRelativePath) throws IOException {
    File from = new File(getTestDataPath(fromRelativePath));
    File to = new File(getTestDataPath(toRelativePath));

    if (from.isDirectory()) FileUtil.copyDir(from, to);
    else FileUtil.copy(from, to);

    LocalFileSystem.getInstance().refreshIoFiles(Collections.singleton(to));
  }
}
