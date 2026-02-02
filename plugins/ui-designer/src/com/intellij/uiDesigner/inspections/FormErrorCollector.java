// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.inspections;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public abstract class FormErrorCollector {
  public abstract void addError(final String inspectionId, @NotNull IComponent component, @Nullable IProperty prop,
                                @NotNull @InspectionMessage String errorMessage,
                                EditorQuickFixProvider @NotNull ... editorQuickFixProvider);
}
