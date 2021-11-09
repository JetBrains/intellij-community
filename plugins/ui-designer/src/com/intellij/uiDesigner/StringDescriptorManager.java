// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner;

import com.intellij.ProjectTopics;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesUtilBase;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Pair;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;


public final class StringDescriptorManager {
  private Module myModule;
  private final Map<Pair<Locale, String>, PropertiesFile> myPropertiesFileCache = ContainerUtil.createSoftValueMap();

  public StringDescriptorManager(@NotNull Module module) {
    myModule = module;
    module.getProject().getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        synchronized(myPropertiesFileCache) {
          myPropertiesFileCache.clear();
        }
      }
    });
  }

  public static StringDescriptorManager getInstance(Module module) {
    StringDescriptorManager service = module.getService(StringDescriptorManager.class);
    if (service != null) {
      service.myModule = module;
    }
    return service;
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

    IProperty prop = resolveToProperty(descriptor, locale);
    if (prop != null) {
      final String value = prop.getUnescapedValue();
      if (value != null) {
        return value;
      }
    }
    // We have to return surrogate string in case if propFile name is invalid or bundle doesn't have specified key
    return "[" + descriptor.getKey() + " / " + descriptor.getBundleName() + "]";
  }

  public IProperty resolveToProperty(@NotNull StringDescriptor descriptor, @Nullable Locale locale) {
    String propFileName = descriptor.getDottedBundleName();
    Pair<Locale, String> cacheKey = Pair.create(locale, propFileName);
    PropertiesFile propertiesFile;
    synchronized (myPropertiesFileCache) {
      propertiesFile = myPropertiesFileCache.get(cacheKey);
    }
    if (propertiesFile == null || !propertiesFile.getContainingFile().isValid()) {
      propertiesFile = PropertiesUtilBase.getPropertiesFile(propFileName, myModule, locale);
      synchronized (myPropertiesFileCache) {
        if (propertiesFile != null) {
          myPropertiesFileCache.put(cacheKey, propertiesFile);
        }
      }
    }

    if (propertiesFile != null) {
      final IProperty propertyByKey = propertiesFile.findPropertyByKey(descriptor.getKey());
      if (propertyByKey != null) {
        return propertyByKey;
      }
    }
    return null;
  }
}
