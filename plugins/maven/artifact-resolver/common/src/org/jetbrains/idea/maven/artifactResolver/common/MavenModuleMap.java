/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.artifactResolver.common;

import org.apache.maven.artifact.Artifact;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * @author Sergey Evdokimov
 */
public class MavenModuleMap {

  private static final MavenModuleMap ourInstance = new MavenModuleMap();

  public static final String PATHS_FILE_PROPERTY = "idea.modules.paths.file";

  private final Properties myMap = new Properties();

  private MavenModuleMap() {
    String path = System.getProperty(PATHS_FILE_PROPERTY);
    if(path != null) {
      try {
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(path));
        try {
          myMap.load(in);
        } finally {
          in.close();
        }
      } catch(IOException e) {
        // XXX log
      }
    }
  }

  public static MavenModuleMap getInstance() {
    return ourInstance;
  }

  public boolean resolveToModule(Artifact artifact) {
    String extension = artifact.getArtifactHandler().getExtension();
    if ("jar".equals(extension) && "test-jar".equals(artifact.getType())) {
      extension = "test-jar";
    }

    File file = findArtifact(artifact.getGroupId(), artifact.getArtifactId(), extension, artifact.getBaseVersion());

    if(file == null) {
      return false;
    }

    artifact.setFile(file);
    artifact.setResolved(true);
    return true;
  }

  public File findArtifact(String groupId, String artifactId, String type, String baseVersion) {
    String key = groupId + ':' + artifactId + ':' + type + ':' + baseVersion;
    String value = myMap.getProperty(key);

    if(value == null || value.length() == 0) {
      return null;
    }

    File file = new File(value);
    if(!file.exists()) {
      return null;
    }

    return file;
  }

}
