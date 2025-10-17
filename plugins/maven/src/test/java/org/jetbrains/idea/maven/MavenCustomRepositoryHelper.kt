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
import org.jetbrains.idea.maven.utils.MavenLog;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;

public class MavenCustomRepositoryHelper {

  private final Path myWorkingData;

  public MavenCustomRepositoryHelper(Path tempDir, String... subFolders) throws IOException {
    myWorkingData = tempDir.resolve("testData");
    Files.createDirectories(myWorkingData);
    for (String each : subFolders) {
      addTestData(each);
    }
  }

  public void addTestData(String relativePath) throws IOException {
    addTestData(relativePath, relativePath);
  }

  public void addTestData(String relativePathFrom, String relativePathTo) throws IOException {
    Path to = myWorkingData.resolve(relativePathTo);
    Path from = Paths.get(getOriginalTestDataPath(), relativePathFrom);
    Files.createDirectories(to.getParent());
    Files.walkFileTree(from, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Path target = to.resolve(from.relativize(file));
        Files.createDirectories(target.getParent());
        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
        return FileVisitResult.CONTINUE;
      }
    });
    LocalFileSystem.getInstance().refreshNioFiles(Collections.singleton(to));
    LocalFileSystem.getInstance().refreshNioFiles(Collections.singleton(to));
  }

  public static String getOriginalTestDataPath() {
    String sourcesDir = System.getProperty("maven.sources.dir", PluginPathManager.getPluginHomePath("maven"));
    return FileUtil.toSystemIndependentName(sourcesDir + "/src/test/data");
  }

  public Path getTestData(String relativePath) {
    return myWorkingData.resolve(relativePath);
  }

  public void delete(String relativePath) {
    try {
      Path path = getTestData(relativePath);
      MavenLog.LOG.warn("Deleting " + path);
      if (Files.isDirectory(path)) {
        // delete directory content recursively
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
          }
        });
      }
      else {
        Files.deleteIfExists(path);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void copy(String fromRelativePath, String toRelativePath) throws IOException {
    Path from = getTestData(fromRelativePath);
    Path to = getTestData(toRelativePath);

    if (Files.isDirectory(from)) {
      Files.walkFileTree(from, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Path target = to.resolve(from.relativize(file));
          Files.createDirectories(target.getParent());
          Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
          return FileVisitResult.CONTINUE;
        }
      });
    }
    else {
      Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
    }

    LocalFileSystem.getInstance().refreshNioFiles(Collections.singleton(to));
  }
}
