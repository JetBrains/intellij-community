// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  public static final class Data implements Serializable {
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
