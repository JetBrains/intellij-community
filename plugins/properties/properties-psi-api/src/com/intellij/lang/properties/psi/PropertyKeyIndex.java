// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.lang.properties.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class PropertyKeyIndex extends StringStubIndexExtension<Property> {
  public static final StubIndexKey<String, Property> KEY = StubIndexKey.createIndexKey("properties.index");

  private static final PropertyKeyIndex ourInstance = new PropertyKeyIndex();

  public static PropertyKeyIndex getInstance() {
    return ourInstance;
  }

  @Override
  public @NotNull StubIndexKey<String, Property> getKey() {
    return KEY;
  }

  /**
   * @deprecated Deprecated base method, please use {@link #getProperties(String, Project, GlobalSearchScope)}
   */
  @Deprecated
  @Override
  public Collection<Property> get(@NotNull String key, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    return getProperties(key, project, scope);
  }

  public Collection<Property> getProperties(@NotNull String key, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    return StubIndex.getElements(getKey(), key, project, scope, Property.class);
  }
}
