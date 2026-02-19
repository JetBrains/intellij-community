// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.maven.server.workspace;

import org.eclipse.aether.artifact.Artifact;
import org.jetbrains.maven.server.IntellijMavenSpy;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public final class MavenModuleMap {

  private static final MavenModuleMap ourInstance = new MavenModuleMap();

  public static final String PATHS_FILE_PROPERTY = "idea.modules.paths.file";

  private final Properties myMap = new Properties();

  private MavenModuleMap() {
    String path = System.getProperty(PATHS_FILE_PROPERTY);
    if (path != null) {
      IntellijMavenSpy.printInternalLogging("reading idea.modules.paths from " + path);
      try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(Paths.get(path)))) {
        myMap.load(in);
        IntellijMavenSpy.printInternalLogging("have read " + myMap.size() + " records");
      }
      catch (IOException e) {
        IntellijMavenSpy.printInternalLogging(e.getMessage());
      }
    }
    else {
      IntellijMavenSpy.printInternalLogging("idea.modules.paths.file is not defined");
    }
  }

  public static MavenModuleMap getInstance() {
    return ourInstance;
  }

  @SuppressWarnings("IO_FILE_USAGE")
  public File findArtifact(Artifact artifact) {
    String key = getKey(artifact);
    String value = myMap.getProperty(key);
    if (value == null || value.isEmpty()) {
      return null;
    }

    File file = new File(value);
    if (!file.exists()) {
      return null;
    }

    return file;
  }

  private static String getKey(Artifact artifact) {
    String type = getType(artifact);
    return artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + type + ':' + artifact.getBaseVersion();
  }

  private static String getType(Artifact artifact) {
    String extension = artifact.getExtension();
    String classifier = artifact.getClassifier();

    if ("jar".equals(extension) && classifier != null && !classifier.isEmpty()) {
      if ("tests".equals(classifier) || "test-jar".equals(classifier)) {
        return "test-jar";
      }
      return classifier;
    }
    else {
      return extension;
    }
  }
}
