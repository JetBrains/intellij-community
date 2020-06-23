// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.make;

import com.intellij.openapi.compiler.CompilableFileTypesProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.uiDesigner.GuiFormFileType;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public class FormTypeRegistrar implements CompilableFileTypesProvider {
  @Override
  public @NotNull Set<FileType> getCompilableFileTypes() {
    return Collections.singleton(GuiFormFileType.INSTANCE);
  }
}