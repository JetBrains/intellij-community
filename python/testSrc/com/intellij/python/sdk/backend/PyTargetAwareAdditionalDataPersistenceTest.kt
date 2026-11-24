// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.PathMappingSettings
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.flavors.UnixPythonSdkFlavor
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * The stored form of the data of a target SDK. A form that the save does not write again asks for a migration, so that the
 * data of the SDK and its stored entity become equal.
 */
@TestFor(issues = ["PY-92927"])
@Subsystems.RemoteInterpreters
@Layers.Functional
@TestApplication
internal class PyTargetAwareAdditionalDataPersistenceTest {
  @BeforeEach
  fun registerTargetType(@TestDisposable disposable: Disposable) {
    TargetEnvironmentType.EXTENSION_NAME.point.registerExtension(TestTargetType(), disposable)
  }

  @Test
  fun `the data stored as its save writes it needs no migration`() {
    val loaded = targetSdkData(null).apply { load(savedTargetSdkData()) }

    assertThat(loaded.migrateAdditionalData(null)).isFalse()
  }

  @Test
  fun `the data stored with a flavor that no plugin registers needs no migration and keeps the flavor`() {
    val element = savedTargetSdkData()
    element.flavorIdSetting().setAttribute("value", "NoSuchFlavor")

    val loaded = targetSdkData(null).apply { load(element) }

    assertThat(loaded.migrateAdditionalData(null)).isFalse()
    assertThat(JDOMUtil.write(Element("sdk-data").also { loaded.save(it) })).isEqualTo(JDOMUtil.write(element))
  }

  @Test
  fun `the data stored with the unknown flavor is migrated to the Unix flavor`() {
    val element = savedTargetSdkData()
    element.flavorIdSetting().setAttribute("value", PythonSdkFlavor.UnknownFlavor.INSTANCE.uniqueId)

    val migrated = assertMigratesOnce(element)

    assertThat(migrated.flavorIdSetting().getAttributeValue("value")).isEqualTo(UnixPythonSdkFlavor.getInstance().uniqueId)
  }

  @Test
  fun `the data stored without the id of its target is migrated to a stable id`() {
    val element = savedTargetSdkData()
    element.getChild(TARGET_ELEMENT).removeAttribute("uuid")

    val migrated = assertMigratesOnce(element)

    assertThat(migrated.getChild(TARGET_ELEMENT).getAttributeValue("uuid")).isNotBlank()
  }

  @Test
  fun `the data stored with a legacy attribute is migrated`() {
    val element = savedTargetSdkData()
    element.setAttribute("INITIALIZED", "false")

    val migrated = assertMigratesOnce(element)

    assertThat(migrated.getAttributeValue("INITIALIZED")).isNull()
  }

  @Test
  fun `the data stored with its target before its path mappings is migrated`() {
    val element = savedTargetSdkData()
    val target = element.getChild(TARGET_ELEMENT)
    element.removeContent(target)
    element.addContent(element.indexOf(element.getChild("PathMappingSettings")), target)

    assertMigratesOnce(element)
  }

  /** Loads [element] and migrates it. The migrated form needs no second migration, and its save writes it again. */
  private fun assertMigratesOnce(element: Element): Element {
    val loaded = targetSdkData(null).apply { load(element) }
    assertThat(loaded.migrateAdditionalData(null)).describedAs("a stored form that the save does not write again is migrated").isTrue()
    val migrated = Element("sdk-data").also { loaded.save(it) }

    val reloaded = targetSdkData(null).apply { load(migrated) }
    assertThat(reloaded.migrateAdditionalData(null)).describedAs("the migrated form needs no second migration").isFalse()
    assertThat(JDOMUtil.write(Element("sdk-data").also { reloaded.save(it) })).isEqualTo(JDOMUtil.write(migrated))
    return migrated
  }

  /** The XML of the data of a target SDK with path mappings, as its save writes it. */
  private fun savedTargetSdkData(): Element {
    val data = targetSdkData(pythonSdkTarget(root = "/srv/app")).apply {
      pathMappings.add(PathMappingSettings.PathMapping("/projects/app", "/srv/app"))
    }
    return Element("sdk-data").also { data.save(it) }
  }

  private fun Element.flavorIdSetting(): Element = getChildren("setting").single { it.getAttributeValue("name") == "FLAVOR_ID" }

  private fun targetSdkData(target: TargetEnvironmentConfiguration?): PyTargetAwareAdditionalData =
    PyTargetAwareAdditionalData(PyFlavorAndData(PyFlavorData.Empty, UnixPythonSdkFlavor.getInstance()), Path.of(""), target).apply {
      interpreterPath = "/usr/bin/python3"
    }

  private companion object {
    const val TARGET_ELEMENT = "targetEnvironmentConfiguration"
  }
}
