// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.unresolvedReference

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PythonVisitorFilter
import org.jetbrains.annotations.ApiStatus

/**
 * Disables [PyUnresolvedReferencesInspection] for Python injected into a docstring code block
 * or a doctest fragment when the injection does not live in a real Python file.
 * The inspection needs a real file for its reference index.
 */
@ApiStatus.Internal
class PyInjectedUnresolvedReferencesVisitorFilter : PythonVisitorFilter {
  override fun isSupported(visitorClass: Class<out PyElementVisitor?>, file: PsiFile): Boolean {
    if (visitorClass != PyUnresolvedReferencesInspection::class.java) return true
    val topLevelFile = InjectedLanguageManager.getInstance(file.project).getTopLevelFile(file)
    return topLevelFile is PyFile
  }
}
