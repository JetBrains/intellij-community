// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.interpreter

import com.intellij.openapi.util.JDOMUtil
import com.intellij.python.sdk.backend.PyInterpreterLibraryName
import com.intellij.python.sdk.backend.PyInterpreterLibraryProperties
import com.intellij.python.sdk.backend.evolution.PyEvoEnvironmentProvider
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.xmlb.XmlSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.StringReader

/**
 * The Libraries page of a Java IDE draws an interpreter library with the icon of the tool that manages its interpreter,
 * as the project view of PyCharm does. The icon comes from the library properties that the synchronization writes.
 */
@TestApplication
internal class PyInterpreterLibraryIconTest {
  @Test
  fun `the properties of an interpreter library name the tool of its interpreter`() {
    val name = checkNotNull(PyInterpreterLibraryName.parse("Python: . native:uv:.venv"))
    // The platform loads the properties of a library entity so, see `LibraryStateSnapshot.loadProperties`.
    val element = JDOMUtil.load(StringReader(PyInterpreterLibraryProperties.xmlTagOf(name)))
    val properties = PyInterpreterLibraryProperties()
    properties.loadState(XmlSerializer.deserialize(element, PyInterpreterLibraryProperties.State::class.java))
    assertThat(properties.manager).isEqualTo(name.ref.manager.value)
  }

  @Test
  fun `an interpreter library has the icon of its tool`() {
    val provider = PyEvoEnvironmentProvider.EP_NAME.extensionList.first()
    assertThat(PyInterpreterLibraryProperties(provider.tool.fusId.value).toolIcon).isSameAs(provider.icon)
  }

  @Test
  fun `an interpreter library of an unknown tool has no tool icon`() {
    assertThat(PyInterpreterLibraryProperties("no-such-tool").toolIcon).isNull()
  }
}
