/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.*;

public class MavenWorkspaceMap implements Serializable {
  private final Map<MavenId, Data> myMapping = new HashMap<MavenId, Data>();

  public void register(@NotNull MavenId id, @NotNull File file) {
    register(id, file, null);
  }
  
  public void register(@NotNull MavenId id, @NotNull File file, @Nullable File outputFile) {
    for (MavenId each : getAllIDs(id)) {
      myMapping.put(each, new Data(id, file, outputFile));
    }
  }

  public void unregister(@NotNull MavenId id) {
    for (MavenId each : getAllIDs(id)) {
      myMapping.remove(each);
    }
  }

  @Nullable
  public Data findFileAndOriginalId(@NotNull MavenId dependencyId) {
    return myMapping.get(dependencyId);
  }

  @NotNull
  public Set<MavenId> getAvailableIds() {
    return myMapping.keySet();
  }

  private static MavenId[] getAllIDs(MavenId id) {
    String version = id.getVersion();
    if (version != null && version.contains("SNAPSHOT")) {
      return new MavenId[]{id, new MavenId(id.getGroupId(), id.getArtifactId(), "LATEST")};
    }
    else {
      return new MavenId[]{id,
        new MavenId(id.getGroupId(), id.getArtifactId(), "LATEST"),
        new MavenId(id.getGroupId(), id.getArtifactId(), "RELEASE")};
    }
  }

  public MavenWorkspaceMap copy() {
    MavenWorkspaceMap result = new MavenWorkspaceMap();
    result.myMapping.putAll(myMapping);
    return result;
  }

  public static class Data implements Serializable {
    public final MavenId originalId;
    private final File file;
    private final File outputFile;

    private Data(MavenId originalId, File file, File outputFile) {
      this.originalId = originalId;
      this.file = file;
      this.outputFile = outputFile;
    }

    public File getFile(String type) {
      return outputFile == null || MavenConstants.POM_EXTENSION.equalsIgnoreCase(type) ? file : outputFile;
    }
  }
}
