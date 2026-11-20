// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.util.JDOMUtil
import com.intellij.python.pytools.common.FusId
import com.intellij.python.sdk.backend.evolution.PyEvoEnvironmentProvider
import com.intellij.util.xmlb.annotations.Attribute
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * The properties of an interpreter library, see [PyInterpreterLibraryName]: the tool that manages its interpreter, see
 * [com.intellij.python.sdk.common.PyInterpreterRef.manager]. The library type draws the library with the icon of that
 * tool, as the project view does.
 *
 * The synchronization writes them from the name of the library, see [xmlTagOf]. No file stores them.
 */
@ApiStatus.Internal
class PyInterpreterLibraryProperties(var manager: String? = null) : LibraryProperties<PyInterpreterLibraryProperties.State>() {
  class State {
    @Attribute(MANAGER)
    var manager: String? = null
  }

  /** The icon of the tool, or `null` when no provider has the tool. */
  val toolIcon: Icon?
    get() = manager?.let { PyEvoEnvironmentProvider.iconOf(FusId(it)) }

  override fun getState(): State = State().also { it.manager = manager }

  override fun loadState(state: State) {
    manager = state.manager
  }

  override fun equals(other: Any?): Boolean = other is PyInterpreterLibraryProperties && other.manager == manager

  override fun hashCode(): Int = manager.hashCode()

  companion object {
    private const val MANAGER = "manager"

    /** The XML of the properties of the interpreter library [name], as a library properties entity holds it. */
    fun xmlTagOf(name: PyInterpreterLibraryName): String =
      JDOMUtil.write(Element("properties").setAttribute(MANAGER, name.ref.manager.value))
  }
}
