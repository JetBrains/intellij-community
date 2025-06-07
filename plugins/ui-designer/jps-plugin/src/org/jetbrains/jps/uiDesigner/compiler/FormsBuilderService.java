// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.uiDesigner.compiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;

import java.util.Arrays;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class FormsBuilderService extends BuilderService{
  @Override
  public @NotNull List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    return Arrays.asList(new FormsBindingManager(), new FormsInstrumenter());
  }
}
