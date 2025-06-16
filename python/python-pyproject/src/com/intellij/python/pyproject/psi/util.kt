package com.intellij.python.pyproject.psi

import com.intellij.psi.PsiFile
import com.intellij.python.pyproject.PY_PROJECT_TOML

fun PsiFile.isPyProjectToml(): Boolean = this.name == PY_PROJECT_TOML
