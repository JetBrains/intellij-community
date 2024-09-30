// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.util.SystemInfoRt;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

// TODO: move it in to open api module
public final class NioFiles {
  public static void rename(@NotNull Path source, @NotNull Path target) throws IOException {
    try {
      Files.move(source, target);
      return;
    }
    catch (IOException e) {
      // If move failed, proceed to copy and delete
    }

    if (!Files.exists(source)) {
      return;
    }

    copy(source, target);
    delete(source);
  }

  private static void copy(@NotNull Path source, @NotNull Path target) throws IOException {
    if (Files.isDirectory(source)) {
      Files.createDirectories(target);
      try (DirectoryStream<Path> entries = Files.newDirectoryStream(source)) {
        for (Path entry : entries) {
          copy(entry, target.resolve(entry.getFileName()));
        }
      }
    }
    else {
      Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void delete(@NotNull Path path) throws IOException {
    if (Files.isDirectory(path)) {
      try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
        for (Path entry : entries) {
          delete(entry);
        }
      }
      Files.delete(path);
    }
    else {
      Files.delete(path);
    }
  }

  public static void rename(@NotNull Path source, @NotNull String newName) throws IOException {
    Path target = source.resolveSibling(newName);
    String sourceName = source.getFileName().toString();

    if (!SystemInfoRt.isFileSystemCaseSensitive && newName.equalsIgnoreCase(sourceName)) {
      // On case-insensitive file systems, renaming a file to a name that differs only by case
      // requires an intermediate file to avoid name collisions.
      Path intermediate = Files.createTempFile(source.getParent(), sourceName, ".tmp");

      try {
        Files.deleteIfExists(intermediate); // Ensure the intermediate file does not exist

        Files.move(source, intermediate, StandardCopyOption.REPLACE_EXISTING);
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
      }
      finally {
        // Clean up the intermediate file if it still exists
        Files.deleteIfExists(intermediate);
      }
    }
    else {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
