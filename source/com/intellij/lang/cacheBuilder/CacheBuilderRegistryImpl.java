/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.lang.cacheBuilder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;
import com.intellij.openapi.fileTypes.FileType;

import java.util.Map;
import java.util.HashMap;

/**
 * @author yole
 */
public class CacheBuilderRegistryImpl extends CacheBuilderRegistry {
  private Map<FileType, WordsScanner> myMap = new HashMap<FileType, WordsScanner>();

  public void registerCacheBuilder(@NotNull FileType fileType, WordsScanner cacheBuilder) {
    myMap.put(fileType, cacheBuilder);
  }

  @Nullable
  public WordsScanner getCacheBuilder(@NotNull FileType fileType) {
    return myMap.get(fileType);
  }

  @NonNls @NotNull
  public String getComponentName() {
    return "CacheBuilderRegistry";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
