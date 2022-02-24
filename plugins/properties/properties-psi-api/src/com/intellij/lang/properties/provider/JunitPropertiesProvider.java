// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.provider;

import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.PropertyKeyIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.properties.provider.PropertiesProvider;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.module.Module;

import java.util.Collection;

public class JunitPropertiesProvider implements PropertiesProvider {
  @Override
  public boolean hasProperty(@NotNull Module classModule,
                             @NotNull String propertyKey,
                             @NotNull String propertyValue,
                             @NotNull GlobalSearchScope scope) {
    Collection<Property> property =
      PropertyKeyIndex.getInstance().get(propertyKey, classModule.getProject(), scope);
    if (property == null) return false;
    Property item = ContainerUtil.getFirstItem(property);
    if (item == null) return false;
    String value = item.getValue();
    return propertyValue.equalsIgnoreCase(value);
  }
}
