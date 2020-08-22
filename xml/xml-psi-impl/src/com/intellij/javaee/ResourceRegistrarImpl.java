// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.javaee;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public final class ResourceRegistrarImpl implements ResourceRegistrar {
  private final Map<String, Map<String, ExternalResourceManagerExImpl.Resource>> myResources = new HashMap<>();
  private final List<String> myIgnored = new ArrayList<>();

  @Override
  public void addStdResource(@NonNls String resource, @NonNls String fileName) {
    addStdResource(resource, null, fileName, getClass());
  }

  @Override
  public void addStdResource(@NonNls String resource, @NonNls String fileName, Class klass) {
    addStdResource(resource, null, fileName, klass);
  }

  public void addStdResource(@NonNls String resource, @NonNls String version, @NonNls String fileName, @Nullable Class<?> klass, @Nullable ClassLoader classLoader) {
    Map<String, ExternalResourceManagerExImpl.Resource> map = ExternalResourceManagerExImpl.getOrCreateMap(myResources, version);
    map.put(resource, new ExternalResourceManagerExImpl.Resource(fileName, klass, classLoader));
  }

  @Override
  public void addStdResource(@NonNls String resource, @Nullable @NonNls String version, @NonNls String fileName, Class klass) {
    addStdResource(resource, version, fileName, klass, null);
  }

  @Override
  public void addIgnoredResource(@NonNls String url) {
    myIgnored.add(url);
  }

  public void addInternalResource(@NonNls String resource, @NonNls String fileName) {
    addInternalResource(resource, null, fileName, getClass());
  }

  public void addInternalResource(@NonNls String resource, @NonNls String fileName, Class<?> clazz) {
    addInternalResource(resource, null, fileName, clazz);
  }

  public void addInternalResource(@NonNls String resource, @NonNls String version, @NonNls String fileName) {
    addInternalResource(resource, version, fileName, getClass());
  }

  public void addInternalResource(@NonNls String resource, @Nullable @NonNls String version, @NonNls String fileName, @Nullable Class<?> clazz) {
    addStdResource(resource, version, ExternalResourceManagerEx.STANDARD_SCHEMAS + fileName, clazz, null);
  }

  public @NotNull Map<String, Map<String, ExternalResourceManagerExImpl.Resource>> getResources() {
    return myResources;
  }

  @NotNull
  public List<String> getIgnored() {
    return myIgnored;
  }
}
