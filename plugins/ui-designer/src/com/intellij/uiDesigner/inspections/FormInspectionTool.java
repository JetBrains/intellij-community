/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.inspections;

import com.intellij.psi.PsiElement;
import com.intellij.uiDesigner.ErrorInfo;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IRootContainer;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public interface FormInspectionTool {
  ExtensionPointName<FormInspectionTool> EP_NAME = new ExtensionPointName<>("com.intellij.uiDesigner.formInspectionTool");

  @NonNls
  String getShortName();
  void startCheckForm(IRootContainer radRootContainer);
  void doneCheckForm(IRootContainer radRootContainer);

  @Nullable
  ErrorInfo[] checkComponent(@NotNull GuiEditor editor, @NotNull RadComponent component);

  boolean isActive(PsiElement psiRoot);

}
