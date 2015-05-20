/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.application.options.emmet;

import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
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

  @NotNull
  public static List<Configurable> getAvailableConfigurables() {
    List<Configurable> configurables = ContainerUtil.newSmartList();
    for (ZenCodingGenerator zenCodingGenerator : ZenCodingGenerator.getInstances()) {
      ContainerUtil.addIfNotNull(configurables, zenCodingGenerator.createConfigurable());
    }
    return configurables;
  }
}
