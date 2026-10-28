// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectView

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElementNode
import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.evolution.findEvoPyProjectIfReady
import com.intellij.python.sdk.backend.NativePythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.evolution.PyEvoEnvironmentProvider
import com.intellij.python.sdk.common.PyInterpreterRef
import com.intellij.python.sdk.backend.TargetPythonInterpreter
import com.jetbrains.python.parser.icons.PythonParserIcons
import javax.swing.Icon
import kotlin.io.path.pathString

/**
 * The interpreter library of a module in the project view. It is drawn as the SDK of an interpreter is drawn, with the
 * path of the interpreter as the location, or as the tooltip when the view hides locations. Its icon is the icon of the
 * tool that manages the interpreter, or the Python icon when no tool has one.
 */
internal class PyInterpreterLibraryNode(project: Project, value: NamedLibraryElement, settings: ViewSettings?) :
  NamedLibraryElementNode(project, value, settings) {
  override fun update(presentation: PresentationData) {
    super.update(presentation)
    val interpreter = value?.orderEntry?.ownerModule?.findEvoPyProjectIfReady()?.interpreter
    presentation.setIcon(interpreter?.let(::toolIconOf) ?: PythonParserIcons.PythonFile)
    if (interpreter == null) return
    val path = when (interpreter) {
      is NativePythonInterpreter -> interpreter.pythonBinary.pathString
      is TargetPythonInterpreter -> interpreter.pythonBinary.toStringOnRemoteMachine()
    }
    if (settings.isShowURL) {
      presentation.locationString = path
    }
    else {
      presentation.tooltip = path
    }
  }
}

/** The icon of the tool that manages [interpreter], see [PyInterpreterRef.manager], or `null` when no provider has it. */
private fun toolIconOf(interpreter: PythonInterpreter): Icon? =
  PyEvoEnvironmentProvider.EP_NAME.extensionList.firstOrNull { it.tool.fusId == interpreter.ref.manager }?.icon
