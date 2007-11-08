/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner;

import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.reference.SoftReference;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.util.messages.MessageBus;
import com.intellij.ProjectTopics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author yole
 */
public class StringDescriptorManager {
  private Module myModule;
  private final Map<Pair<Locale, String>, SoftReference<PropertiesFile>> myPropertiesFileCache = new HashMap<Pair<Locale, String>, SoftReference<PropertiesFile>>();

  public StringDescriptorManager(final Module module, MessageBus bus) {
    myModule = module;
    bus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(final ModuleRootEvent event) {
      }

      public void rootsChanged(final ModuleRootEvent event) {
        synchronized(myPropertiesFileCache) {
          myPropertiesFileCache.clear();
        }
      }
    });
  }

  public static StringDescriptorManager getInstance(Module module) {
    return ModuleServiceManager.getService(module, StringDescriptorManager.class);
  }

  @Nullable public String resolve(@NotNull RadComponent component, @Nullable StringDescriptor descriptor) {
    RadRootContainer root = (RadRootContainer) FormEditingUtil.getRoot(component);
    Locale locale = (root != null) ? root.getStringDescriptorLocale() : null;
    return resolve(descriptor, locale);
  }

  @Nullable public String resolve(@Nullable StringDescriptor descriptor, @Nullable Locale locale) {
    if (descriptor == null) {
      return null;
    }

    if (descriptor.getValue() != null) {
      return descriptor.getValue();
    }

    Property prop = resolveToProperty(descriptor, locale);
    if (prop != null) {
      final String value = prop.getUnescapedValue();
      if (value != null) {
        return value;
      }
    }
    // We have to return surrogate string in case if propFile name is invalid or bundle doesn't have specified key
    return "[" + descriptor.getKey() + " / " + descriptor.getBundleName() + "]";
  }

  public Property resolveToProperty(@NotNull StringDescriptor descriptor, @Nullable Locale locale) {
    String propFileName = descriptor.getDottedBundleName();
    Pair<Locale, String> cacheKey = new Pair<Locale, String>(locale, propFileName);
    SoftReference<PropertiesFile> propertiesFileRef;
    synchronized (myPropertiesFileCache) {
      propertiesFileRef = myPropertiesFileCache.get(cacheKey);
    }
    PropertiesFile propertiesFile = (propertiesFileRef == null) ? null : propertiesFileRef.get();
    if (propertiesFile == null || !propertiesFile.isValid()) {
      propertiesFile = PropertiesUtil.getPropertiesFile(propFileName, myModule, locale);
      synchronized (myPropertiesFileCache) {
        myPropertiesFileCache.put(cacheKey, new SoftReference<PropertiesFile>(propertiesFile));
      }
    }

    if (propertiesFile != null) {
      final Property propertyByKey = propertiesFile.findPropertyByKey(descriptor.getKey());
      if (propertyByKey != null) {
        return propertyByKey;
      }
    }
    return null;
  }
}
