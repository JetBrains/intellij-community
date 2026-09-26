// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class MavenWorkspaceMap implements Serializable {
  private final Map<MavenId, Data> myMapping = new HashMap<MavenId, Data>();
  private final Map<String, Serializable> myAdditionalContext = new HashMap<>();

  public void register(@NotNull MavenId id, @NotNull File file) {
    register(id, file, null);
  }

  public void register(@NotNull MavenId id, @NotNull File file, @Nullable File outputFile) {
    for (MavenId each : getAllIDs(id)) {
      myMapping.put(each, new Data(id, file, outputFile));
    }
  }

  public void addContext(String id, Serializable contextData) {
    myAdditionalContext.put(id, contextData);
  }

  public @Nullable Serializable getAdditionalContext(String id) {
    return myAdditionalContext.get(id);
  }

  public @NotNull Set<String> getAvailableContextIds() {
    return myAdditionalContext.keySet();
  }

  public void unregister(@NotNull MavenId id) {
    for (MavenId each : getAllIDs(id)) {
      myMapping.remove(each);
    }
  }

  public @Nullable Data findFileAndOriginalId(@NotNull MavenId dependencyId) {
    return myMapping.get(dependencyId);
  }

  public @NotNull Set<MavenId> getAvailableIds() {
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

  public static MavenWorkspaceMap copy(MavenWorkspaceMap workspaceMap, Function<? super String, String> transformer) {
    MavenWorkspaceMap result = new MavenWorkspaceMap();
    for (Map.Entry<MavenId, Data> entry : workspaceMap.myMapping.entrySet()) {
      Data data = entry.getValue();
      File outputFile = data.outputFile == null ? null : new File(transformer.apply(data.outputFile.getAbsolutePath()));
      File file = new File(transformer.apply(data.file.getAbsolutePath()));
      result.myMapping.put(entry.getKey(), new Data(data.originalId, file, outputFile));
    }
    return result;
  }

  public MavenWorkspaceMap copyInto(MavenWorkspaceMap recipient) {
    recipient.myMapping.clear();
    recipient.myMapping.putAll(myMapping);
    return recipient;
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

  public static MavenWorkspaceMap empty() {
    return new MavenWorkspaceMap();
  }
}
