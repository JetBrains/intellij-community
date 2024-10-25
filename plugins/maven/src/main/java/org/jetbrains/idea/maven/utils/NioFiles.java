// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.util.SystemInfoRt;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

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

    copyRecursively(source, target);
    delete(source);
  }

  public static void copyRecursively(@NotNull Path from, @NotNull Path to) throws IOException {
    Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        Path copy = dir == from ? to : to.resolve(from.relativize(dir));
        com.intellij.openapi.util.io.NioFiles.createDirectories(copy);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Path copy = file == from ? to : to.resolve(from.relativize(file));
        Files.copy(file, copy, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
        return FileVisitResult.CONTINUE;
      }
    });
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
