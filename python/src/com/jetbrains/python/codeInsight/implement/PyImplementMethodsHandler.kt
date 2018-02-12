/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.implement

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.python.codeInsight.override.PyOverrideImplementUtil
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.types.TypeEvalContext

class PyImplementMethodsHandler : LanguageCodeInsightActionHandler {

  override fun isValidFor(editor: Editor?, file: PsiFile?): Boolean {
    return editor != null && file is PyFile && PyOverrideImplementUtil.getContextClass(editor, file) != null
  }

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    val cls = PyOverrideImplementUtil.getContextClass(editor, file)
    if (cls != null) {
      PyOverrideImplementUtil.chooseAndImplementMethods(project, editor, cls, TypeEvalContext.codeCompletion(project, file))
    }
  }

  override fun startInWriteAction() = false
}