// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.artifactResolver.common;

import org.apache.maven.artifact.Artifact;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public final class MavenModuleMap {

  private static final MavenModuleMap ourInstance = new MavenModuleMap();

  public static final String PATHS_FILE_PROPERTY = "idea.modules.paths.file";

  private final Properties myMap = new Properties();

  private MavenModuleMap() {
    String path = System.getProperty(PATHS_FILE_PROPERTY);
    if (path != null) {
      try {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(path))) {
          myMap.load(in);
        }
      }
      catch (IOException e) {
        // XXX log
      }
    }
  }

  public static MavenModuleMap getInstance() {
    return ourInstance;
  }

  public boolean resolveToModule(Artifact artifact) {
    String extension = artifact.getArtifactHandler().getExtension();
    File file = findArtifact(artifact.getGroupId(), artifact.getArtifactId(), extension, artifact.getType(), artifact.getBaseVersion());

    if (file == null) {
      return false;
    }

    artifact.setFile(file);
    artifact.setResolved(true);
    return true;
  }

  public File findArtifact(String groupId, String artifactId, String extension, String classifier, String baseVersion) {
    String type = extension;
    if ("jar".equals(type) && classifier != null && !classifier.isEmpty()) {
      type = "tests".equals(classifier) || "test-jar".equals(classifier) ? "test-jar" : classifier;
    }

    String key = groupId + ':' + artifactId + ':' + type + ':' + baseVersion;
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
}
