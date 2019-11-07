// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.emmet;

import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.util.PlatformUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EmmetConfigurableProvider extends ConfigurableProvider {
  @Nullable
  @Override
  public Configurable createConfigurable() {
    final List<Configurable> availableConfigurables = getAvailableConfigurables();
    return availableConfigurables.size() == 1
           ? new EmmetCompositeConfigurable(ContainerUtil.getFirstItem(availableConfigurables))
           : new EmmetCompositeConfigurable(availableConfigurables);
  }

  @Override
  public boolean canCreateConfigurable() {
    return !PlatformUtils.isDataGrip();
  }

  @NotNull
  public static List<Configurable> getAvailableConfigurables() {
    List<Configurable> configurables = new SmartList<>(new XmlEmmetConfigurable());
    for (ZenCodingGenerator zenCodingGenerator : ZenCodingGenerator.getInstances()) {
      ContainerUtil.addIfNotNull(configurables, zenCodingGenerator.createConfigurable());
    }
    return configurables;
  }
}
