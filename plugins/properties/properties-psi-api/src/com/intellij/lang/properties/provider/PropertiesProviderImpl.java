// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.provider;

import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.PropertyKeyIndex;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.properties.provider.PropertiesProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class PropertiesProviderImpl implements PropertiesProvider {
  @Override
  public @Nullable String getPropertyValue(@NotNull String propertyKey, @NotNull GlobalSearchScope scope) {
    Project project = scope.getProject();
    if (project == null) return null;
    Collection<Property> property =
      PropertyKeyIndex.getInstance().get(propertyKey, project, scope);
    if (property == null) return null;
    Property item = ContainerUtil.getFirstItem(property);
    return item != null ? item.getValue() : null;
  }
}
