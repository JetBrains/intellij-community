// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.util.xmlb.XmlSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class PyPackageRequirementsInspectionSerializationTest {

  @Test
  fun `ignoredPackages survives XML round-trip`() {
    val inspection = PyPackageRequirementsInspection()
    inspection.ignoredPackages.addAll(listOf("numpy", "pandas", "requests"))

    val element = XmlSerializer.serialize(inspection)
    val restored = XmlSerializer.deserialize(element, PyPackageRequirementsInspection::class.java)

    assertEquals(inspection.ignoredPackages, restored.ignoredPackages)
  }

  @Test
  fun `ignoredPackages can be set and retrieved via OptionController and survives XML round-trip`() {
    val packages = listOf("numpy", "pandas")
    val inspection = PyPackageRequirementsInspection()

    assertDoesNotThrow {
      inspection.optionController.setOption("ignoredPackages", packages)
    }
    assertEquals(packages, inspection.optionController.getOption("ignoredPackages"))

    val element = XmlSerializer.serialize(inspection)
    val restored = XmlSerializer.deserialize(element, PyPackageRequirementsInspection::class.java)
    assertEquals(packages, restored.optionController.getOption("ignoredPackages"))
  }

  @Test
  fun `ignoredPackages is empty by default after deserialization`() {
    val inspection = PyPackageRequirementsInspection()

    val element = XmlSerializer.serialize(inspection)
    val restored = XmlSerializer.deserialize(element, PyPackageRequirementsInspection::class.java)

    assertEquals(emptyList<String>(), restored.ignoredPackages)
  }
}
