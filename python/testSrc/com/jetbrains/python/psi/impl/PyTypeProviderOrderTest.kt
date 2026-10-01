// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.intellij.idea.TestFor
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.testFramework.UsefulTestCase
import com.jetbrains.python.PythonTestUtil
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Pins the effective order of the `Pythonid.typeProvider` extensions of the PythonCore plugin.
 *
 * The first provider with a result wins, so a change of the order can change an inferred type.
 * Each line holds the implementation class and its `order` attribute. Each registration needs an explicit order, so
 * the order does not depend on the load order of the descriptors.
 * The test skips the providers of other plugins, because the set of plugins differs between the community and the full product.
 */
@Subsystems.CodeInsight
@Layers.Functional
@TestFor(classes = [PyTypeProvider::class])
class PyTypeProviderOrderTest : PyCodeInsightTestCase() {

  @Test
  fun `type provider order is unchanged`() {
    val point = PyTypeProvider.EP_NAME.point as ExtensionPointImpl<*>
    val adapters = point.sortedAdapters.filter { it.pluginDescriptor.pluginId.idString == PYTHON_CORE_PLUGIN_ID }
    val unordered = adapters.filter { it.order == LoadingOrder.ANY }.map { it.assignableToClassName }
    Assertions.assertEquals(emptyList<String>(), unordered, "Each type provider of PythonCore needs an explicit order")
    val actual = adapters.joinToString("\n", postfix = "\n") { "${it.assignableToClassName} ${it.order}" }
    UsefulTestCase.assertSameLinesWithFile("${PythonTestUtil.getTestDataPath()}/typeProvider/order.txt", actual)
  }

  private companion object {
    const val PYTHON_CORE_PLUGIN_ID = "PythonCore"
  }
}
