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
package com.jetbrains.python.testing.universalTests

import com.intellij.execution.ExecutionException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.util.QualifiedName
import com.jetbrains.commandInterface.commandLine.CommandLineLanguage
import com.jetbrains.commandInterface.commandLine.CommandLinePart
import com.jetbrains.commandInterface.commandLine.psi.CommandLineArgument
import com.jetbrains.commandInterface.commandLine.psi.CommandLineFile
import com.jetbrains.commandInterface.commandLine.psi.CommandLineOption
import com.jetbrains.extensions.getQName
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.resolve.fromModule
import com.jetbrains.python.psi.resolve.resolveModuleAt
import java.util.*

/**
 * @author Ilya.Kazakevich
 */


/**
 * For each element finds its name calculated against closest folder inside of module sources.
 * Having "foo.spam.eggs" where "foo" is plain dir but "spam" and 'eggs' are packages (with init)
 * will return "foo" as folder and 'spam.eggs' as name
 */
internal fun findPathWithPackagesByName(element: PyQualifiedNameOwner): Pair<VirtualFile, QualifiedName>? {
  val module = ModuleUtil.findModuleForPsiElement(element) ?: return null
  val elementQNameStr = element.qualifiedName ?: return null
  var elementQName = QualifiedName.fromDottedString(elementQNameStr)
  var fileQName = (element.containingFile as PyFile).getQName() ?: return null
  val context = fromModule(module)

  val psiManager = PsiManager.getInstance(element.project)

  val root = findPathWithPackagesByFsItem(element.containingFile)?:return null
  val newFolder = psiManager.findDirectory(root) ?: return null
  //var currentName = QualifiedName.fromDottedString(elementQName)
  while (resolveModuleAt(fileQName, newFolder, context).isEmpty() && fileQName.componentCount > 0) {
    fileQName = fileQName.removeHead(1)
    elementQName = elementQName.removeHead(1)
  }
  if (elementQName.componentCount > 0) {
    return Pair(newFolder.virtualFile, elementQName)
  }
  return null
}

/**
 * Same as [findPathWithPackagesByName] but uses fs item instead.
 * For foo/eggs/spam.py will return 'foo' if "eggs" has init.py or "foo/eggs" if not
 *
 */
internal fun findPathWithPackagesByFsItem(elementPath: PsiFileSystemItem): VirtualFile? {
  var currentDir = if (elementPath.isDirectory) {
    elementPath.virtualFile
  }
  else {
    elementPath?.parent?.virtualFile ?: return null
  }

  val projectDir = findVFSItemRoot(elementPath.virtualFile, elementPath.project) ?: return null
  while (VfsUtil.isAncestor(projectDir, currentDir, false) && (currentDir.findChild(PyNames.INIT_DOT_PY) != null)) {
    currentDir = currentDir.parent
  }
  return currentDir
}

/**
 * Finds root closest to some file
 */
private fun findVFSItemRoot(virtualFile: VirtualFile, project: Project): VirtualFile? {
  val module = ModuleUtil.findModuleForFile(virtualFile, project)
  if (module == null) {
    Logger.getInstance(PyUniversalTestConfiguration::class.java).warn("No module for " + virtualFile)
    return null
  }
  return PyUtil.getSourceRoots(module)
           .map {
             val path = VfsUtil.getRelativePath(virtualFile, it) ?: return null
             com.intellij.openapi.util.Pair(path, it)
           }
           .filterNotNull()
           .sortedBy {
             it.first.length
           }
           .map(com.intellij.openapi.util.Pair<String, VirtualFile>::second)
           .firstOrNull() ?: return null

}


/**
 * Emulates command line processor (cmd, bash) by parsing command line to arguments that can be provided as argv.
 * Escape chars are not supported but quotes work.
 * @throws ExecutionException if can't be parsed
 */
fun getParsedAdditionalArguments(project: Project, additionalArguments: String): List<String> {
  val factory = PsiFileFactory.getInstance(project)
  val file = factory.createFileFromText(CommandLineLanguage.INSTANCE,
                                        String.format("fake_command %s", additionalArguments)) as CommandLineFile

  if (file.children.any { it is PsiErrorElement }) {
    throw ExecutionException("Additional arguments can't be parsed. Please check they are valid: $additionalArguments")
  }


  val additionalArgsList = ArrayList<String>()
  var skipArgument = false
  file.children.filterIsInstance(CommandLinePart::class.java).forEach {
    when (it) {
      is CommandLineOption -> {
        val optionText = it.text
        val possibleArgument = it.findArgument()
        if (possibleArgument != null) {
          additionalArgsList.add(optionText + possibleArgument.valueNoQuotes)
          skipArgument = true
        }
        else {
          additionalArgsList.add(optionText)
        }
      }
      is CommandLineArgument -> {
        if (!skipArgument) {
          additionalArgsList.add(it.valueNoQuotes)
        }
        skipArgument = false
      }
    }
  }
  return additionalArgsList
}

