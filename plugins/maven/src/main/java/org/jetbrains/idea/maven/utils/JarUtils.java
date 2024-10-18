// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils;

import com.intellij.util.io.zip.JBZipEntry;
import com.intellij.util.io.zip.JBZipFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

// TODO: move it in to open api module
public final class JarUtils {
  public static @Nullable Properties loadProperties(@NotNull Path file, @NotNull String entryName) {
    if (Files.isReadable(file)) {
      try {
        try (JBZipFile zipFile = new JBZipFile(file.toFile())) {
          JBZipEntry entry = zipFile.getEntry(entryName);
          if (entry != null) {
            Properties properties = new Properties();
            properties.load(entry.getInputStream());
            return properties;
          }
        }
      }
      catch (IOException e) {
        MavenLog.LOG.debug(e);
      }
    }

    return null;
  }

  public static String getJarAttribute(Path file, String entryName, Attributes.Name attribute) {
    if (Files.isReadable(file)) {
      try (
        JBZipFile zipFile = new JBZipFile(file.toFile())
      ) {
        for (JBZipEntry entry : zipFile.getEntries()) {
          if ("META-INF/MANIFEST.MF".equals(entry.getName())) {
            Manifest manifest = new Manifest(entry.getInputStream());
            Attributes attributes = entryName != null ? manifest.getAttributes(entryName) : manifest.getMainAttributes();
            return attributes.getValue(attribute);
          }
        }
      }
      catch (IOException e) {
        MavenLog.LOG.debug(e);
      }
    }
    return null;
  }
}
