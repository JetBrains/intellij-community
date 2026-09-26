// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.remote;

import com.intellij.util.AbstractPathMapper;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * @author Alexander Koshevoy
 */
public class PyRemotePathMapper extends AbstractPathMapper implements Cloneable {
  private final MultiMap<PyPathMappingType, PathMappingSettings.PathMapping> myPathMappings = MultiMap.createSet();

  public static @NotNull PyRemotePathMapper fromSettings(@NotNull PathMappingSettings settings, @NotNull PyPathMappingType mappingType) {
    PyRemotePathMapper mapper = new PyRemotePathMapper();
    mapper.addAll(settings.getPathMappings(), mappingType);
    return mapper;
  }

  public void addMapping(String local, String remote, @NotNull PyPathMappingType type) {
    myPathMappings.putValue(type, new PathMappingSettings.PathMapping(local, remote));
  }

  @Override
  public boolean isEmpty() {
    return myPathMappings.isEmpty();
  }

  @Override
  public @NotNull String convertToLocal(@NotNull String remotePath) {
    for (PyPathMappingType type : PyPathMappingType.values()) {
      String localPath = AbstractPathMapper.convertToLocal(remotePath, myPathMappings.get(type));
      if (localPath != null) {
        return localPath;
      }
    }
    return remotePath;
  }

  @Override
  public @NotNull String convertToRemote(@NotNull String localPath) {
    for (PyPathMappingType type : PyPathMappingType.values()) {
      String remotePath = AbstractPathMapper.convertToRemote(localPath, myPathMappings.get(type));
      if (remotePath != null) {
        return remotePath;
      }
    }
    return localPath;
  }

  private static @NotNull PathMappingSettings.PathMapping clonePathMapping(PathMappingSettings.PathMapping pathMapping) {
    return new PathMappingSettings.PathMapping(pathMapping.getLocalRoot(), pathMapping.getRemoteRoot());
  }

  public void addAll(@NotNull Collection<PathMappingSettings.PathMapping> mappings, @NotNull PyPathMappingType type) {
    for (PathMappingSettings.PathMapping mapping : mappings) {
      myPathMappings.putValue(type, clonePathMapping(mapping));
    }
  }

  @Override
  protected @NotNull Collection<PathMappingSettings.PathMapping> getAvailablePathMappings() {
    return Collections.unmodifiableCollection(myPathMappings.values());
  }

  public enum PyPathMappingType {
    USER_DEFINED,
    /**
     * For example Vagrant synced folders
     */
    REPLICATED_FOLDER,
    SYS_PATH,
    SKELETONS,
    HELPERS
  }

  public static @NotNull PyRemotePathMapper cloneMapper(@Nullable PyRemotePathMapper mapper) {
    PyRemotePathMapper pathMapper = new PyRemotePathMapper();
    if (mapper != null) {
      for (Map.Entry<PyPathMappingType, Collection<PathMappingSettings.PathMapping>> entry : mapper.myPathMappings.entrySet()) {
        for (PathMappingSettings.PathMapping pathMapping : entry.getValue()) {
          pathMapper.addMapping(pathMapping.getLocalRoot(), pathMapping.getRemoteRoot(), entry.getKey());
        }
      }
    }
    return pathMapper;
  }

  @Override
  public PyRemotePathMapper clone() {
    return cloneMapper(this);
  }
}
