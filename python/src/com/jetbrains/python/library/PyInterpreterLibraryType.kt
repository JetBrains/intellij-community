// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.library

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.DummyLibraryProperties
import com.intellij.openapi.roots.libraries.LibraryType
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.sdk.backend.PY_INTERPRETER_LIBRARY_KIND_ID
import com.jetbrains.python.parser.icons.PythonParserIcons
import javax.swing.Icon
import javax.swing.JComponent

/**
 * The type of the interpreter library of a Python project: the library that holds the roots of its interpreter. It
 * draws the library as the interpreter, with the Python icon. The IDE builds such a library itself, so the user cannot
 * create one and has nothing to edit in it.
 */
internal class PyInterpreterLibraryType : LibraryType<DummyLibraryProperties>(PY_INTERPRETER_LIBRARY_KIND) {
  override fun getCreateActionName(): String? = null

  override fun createNewLibrary(parentComponent: JComponent, contextDirectory: VirtualFile?, project: Project): NewLibraryConfiguration? = null

  override fun createPropertiesEditor(editorComponent: LibraryEditorComponent<DummyLibraryProperties>): LibraryPropertiesEditor? = null

  override fun getIcon(properties: DummyLibraryProperties?): Icon = PythonParserIcons.PythonFile
}

private val PY_INTERPRETER_LIBRARY_KIND: PersistentLibraryKind<DummyLibraryProperties> =
  object : PersistentLibraryKind<DummyLibraryProperties>(PY_INTERPRETER_LIBRARY_KIND_ID) {
    override fun createDefaultProperties(): DummyLibraryProperties = DummyLibraryProperties.INSTANCE
  }
