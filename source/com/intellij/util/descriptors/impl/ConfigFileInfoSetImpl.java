/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.descriptors.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.descriptors.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class ConfigFileInfoSetImpl implements ConfigFileInfoSet {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.descriptors.impl.ConfigFileInfoSetImpl");
  @NonNls private static final String ELEMENT_NAME = "deploymentDescriptor";
  @NonNls private static final String ID_AATRIBUTE = "name";
  @NonNls private static final String URL_ATTRIBUTE = "url";
  private MultiValuesMap<ConfigFileMetaData, ConfigFileInfo> myConfigFiles = new MultiValuesMap<ConfigFileMetaData, ConfigFileInfo>();
  private @Nullable ConfigFileContainerImpl myContainer;
  private ConfigFileMetaDataProvider myMetaDataProvider;

  public ConfigFileInfoSetImpl(final @NotNull ConfigFileContainerImpl container) {
    myContainer = container;
    myMetaDataProvider = container.getMetaDataProvider();
  }

  public ConfigFileInfoSetImpl(final ConfigFileMetaDataProvider metaDataProvider) {
    myMetaDataProvider = metaDataProvider;
  }

  public void addConfigFile(ConfigFileInfo descriptor) {
    myConfigFiles.put(descriptor.getMetaData(), descriptor);
    onChange();
  }

  public void addConfigFile(final ConfigFileMetaData metaData, final String url) {
    addConfigFile(new ConfigFileInfo(metaData, url));
  }

  public void removeConfigFile(ConfigFileInfo descriptor) {
    myConfigFiles.remove(descriptor.getMetaData(), descriptor);
    onChange();
  }

  public void replaceConfigFile(final ConfigFileMetaData metaData, final String newUrl) {
    myConfigFiles.removeAll(metaData);
    addConfigFile(new ConfigFileInfo(metaData, newUrl));
  }

  public ConfigFileInfo updateConfigFile(ConfigFile configFile) {
    myConfigFiles.remove(configFile.getMetaData(), configFile.getInfo());
    ConfigFileInfo info = new ConfigFileInfo(configFile.getMetaData(), configFile.getUrl());
    myConfigFiles.put(info.getMetaData(), info);
    ((ConfigFileImpl)configFile).setInfo(info);
    return info;
  }

  public void removeConfigFiles(final ConfigFileMetaData... metaData) {
    for (ConfigFileMetaData data : metaData) {
      myConfigFiles.removeAll(data);
    }
    onChange();
  }

  @NotNull
  public Collection<ConfigFileInfo> getDescriptors(ConfigFileMetaData metaData) {
    final Collection<ConfigFileInfo> collection = myConfigFiles.get(metaData);
    return collection != null ? Collections.unmodifiableCollection(collection) : Collections.<ConfigFileInfo>emptyList();
  }

  @Nullable
  public ConfigFileInfo getConfigFileInfo(ConfigFileMetaData metaData) {
    final Collection<ConfigFileInfo> descriptors = myConfigFiles.get(metaData);
    if (descriptors == null || descriptors.isEmpty()) {
      return null;
    }
    return descriptors.iterator().next();
  }

  public ConfigFileInfo[] getConfigFileInfos() {
    final Collection<ConfigFileInfo> configurations = myConfigFiles.values();
    return configurations.toArray(new ConfigFileInfo[configurations.size()]);
  }

  public void setConfigFileInfos(final Collection<ConfigFileInfo> descriptors) {
    myConfigFiles.clear();
    for (ConfigFileInfo descriptor : descriptors) {
      myConfigFiles.put(descriptor.getMetaData(), descriptor);
    }
    onChange();
  }

  private void onChange() {
    if (myContainer != null) {
      myContainer.updateDescriptors(myConfigFiles);
    }
  }


  public ConfigFileMetaDataProvider getMetaDataProvider() {
    return myMetaDataProvider;
  }

  public void readExternal(final Element element) throws InvalidDataException {
    myConfigFiles.clear();
    List<Element> children = element.getChildren(ELEMENT_NAME);
    for (Element child : children) {
      final String id = child.getAttributeValue(ID_AATRIBUTE);
      if (id != null) {
        final ConfigFileMetaData metaData = myMetaDataProvider.findMetaData(id);
        if (metaData != null) {
          final String url = child.getAttributeValue(URL_ATTRIBUTE);
          myConfigFiles.put(metaData, new ConfigFileInfo(metaData, url));
        }
      }
    }
    onChange();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void writeExternal(final Element element) throws WriteExternalException {
    TreeMap<String, Collection<ConfigFileInfo>> sortedConfigFiles = new TreeMap<String, Collection<ConfigFileInfo>>();
    for (Map.Entry<ConfigFileMetaData,Collection<ConfigFileInfo>> entry : myConfigFiles.entrySet()) {
      String id = entry.getKey().getId();
      sortedConfigFiles.put(id, entry.getValue());
    }
    for (Map.Entry<String, Collection<ConfigFileInfo>> entry : sortedConfigFiles.entrySet()) {
      for (ConfigFileInfo configuration : entry.getValue()) {
        final Element child = new Element(ELEMENT_NAME);
        child.setAttribute(ID_AATRIBUTE, entry.getKey());
        child.setAttribute(URL_ATTRIBUTE, configuration.getUrl());
        //for backward compatibility
        child.setAttribute("optional", "false");
        child.setAttribute("version", configuration.getMetaData().getDefaultVersion().getName());
        element.addContent(child);
      }
    }
  }

  public void setContainer(@NotNull ConfigFileContainerImpl container) {
    LOG.assertTrue(myContainer == null);
    myContainer = container;
    myContainer.updateDescriptors(myConfigFiles);
  }
}
