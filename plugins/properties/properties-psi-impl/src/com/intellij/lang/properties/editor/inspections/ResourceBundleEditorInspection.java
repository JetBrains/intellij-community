/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.lang.properties.editor.inspections;

import com.intellij.codeInspection.*;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public abstract class ResourceBundleEditorInspection extends LocalInspectionTool {

  @Nullable
  public abstract ResourceBundleEditorProblemDescriptor[] checkPropertyGroup(@NotNull List<IProperty> properties,
                                                                             @NotNull ResourceBundle resourceBundle);

  @NotNull
  @Override
  public final PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return PsiElementVisitor.EMPTY_VISITOR;
  }

  @NotNull
  @Override
  public final PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                              boolean isOnTheFly,
                                              @NotNull LocalInspectionToolSession session) {
    return PsiElementVisitor.EMPTY_VISITOR;
  }

  @Nullable
  @Override
  public final ProblemDescriptor[] checkFile(@NotNull PsiFile file,
                                             @NotNull InspectionManager manager,
                                             boolean isOnTheFly) {
    return null;
  }
}
