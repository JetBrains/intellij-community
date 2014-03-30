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
package com.jetbrains.python.codeInsight.override;

import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexey.Ivanov
 */
public class PyOverrideMethodsHandler implements LanguageCodeInsightActionHandler {
  public boolean isValidFor(Editor editor, PsiFile file) {
    return (file instanceof PyFile) && (PyOverrideImplementUtil.getContextClass(file.getProject(), editor, file) != null);
  }

  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PyClass aClass = PyOverrideImplementUtil.getContextClass(project, editor, file);
    if (aClass != null) {
      PyOverrideImplementUtil.chooseAndOverrideMethods(project, editor, aClass);
    }
  }

  public boolean startInWriteAction() {
    return false;
  }
}
