// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.jetbrains.python.requirements.psi.NameReq
import com.jetbrains.python.requirements.psi.Versionspec

fun createFileFromText(project: Project, text: String): RequirementsFile {
  val name = "dummy.txt"
  return PsiFileFactory.getInstance(project).createFileFromText(name, RequirementsFileType.INSTANCE, text) as RequirementsFile
}

fun createNameReq(project: Project, text: String): NameReq? {
  val file = createFileFromText(project, text)
  return file.firstChild as? NameReq
}

fun createVersionspec(project: Project, version: String): Versionspec? {
  val preparedVersion = version.split(',')
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .joinToString(",")
  val nameReq = createNameReq(project, "packageName${preparedVersion}")
  return nameReq?.children?.find { it is Versionspec } as? Versionspec
}