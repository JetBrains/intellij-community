/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.descriptors.impl;

import com.intellij.util.descriptors.ConfigFileMetaData;
import com.intellij.util.descriptors.ConfigFileMetaDataRegistry;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
 */
public class ConfigFileMetaDataRegistryImpl implements ConfigFileMetaDataRegistry {
  private final List<ConfigFileMetaData> myMetaData = new ArrayList<ConfigFileMetaData>();
  private final Map<String, ConfigFileMetaData> myId2MetaData = new HashMap<String, ConfigFileMetaData>();
  private ConfigFileMetaData[] myCachedMetaData;

  public ConfigFileMetaDataRegistryImpl() {
  }

  public ConfigFileMetaDataRegistryImpl(ConfigFileMetaData[] metaDatas) {
    for (ConfigFileMetaData metaData : metaDatas) {
      registerMetaData(metaData);
    }
  }

  @NotNull
  public ConfigFileMetaData[] getMetaData() {
    if (myCachedMetaData == null) {
      myCachedMetaData = myMetaData.toArray(new ConfigFileMetaData[myMetaData.size()]);
    }
    return myCachedMetaData;
  }

  @Nullable
  public ConfigFileMetaData findMetaData(@NonNls @NotNull final String id) {
    return myId2MetaData.get(id);
  }

  public void registerMetaData(@NotNull final ConfigFileMetaData... metaData) {
    for (ConfigFileMetaData data : metaData) {
      myMetaData.add(data);
      myId2MetaData.put(data.getId(), data);
    }
    myCachedMetaData = null;
  }
}
