package org.jetbrains.settingsRepository.test

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.options.BaseSchemeProcessor
import com.intellij.openapi.options.ExternalizableSchemeAdapter
import com.intellij.openapi.options.SchemesManagerImpl
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import org.hamcrest.CoreMatchers.equalTo
import org.jdom.Element
import org.jdom.Parent
import org.junit.Assert.assertThat
import org.junit.Test
import java.io.File

class LoadTest : TestCase() {
  data class TestScheme(element: Element) : ExternalizableSchemeAdapter() {
    val data = JDOMUtil.writeElement(element)

    init {
      myName = element.getChild("component")!!.getAttributeValue("name")!!
    }
  }

  private fun createSchemesManager(dirPath: String): SchemesManagerImpl<TestScheme, TestScheme> {
    return SchemesManagerImpl<TestScheme, TestScheme>(dirPath, object : BaseSchemeProcessor<TestScheme>() {
      override fun writeScheme(scheme: TestScheme?): Parent? {
        throw UnsupportedOperationException()
      }

      override fun readScheme(element: Element): TestScheme? {
        return TestScheme(element)
      }
    }, RoamingType.PER_USER, getProvider(), FileUtil.generateRandomTemporaryPath())
  }

  public Test fun loadScheme() {
    val data = FileUtil.loadFileBytes(File(testDataPath, "local.xml"))
    val dirPath = "\$ROOT_CONFIG$/keymaps"
    save("$dirPath/local.xml", data)

    val schemesManager = createSchemesManager(dirPath)

    schemesManager.loadSchemes()
    val schemes = schemesManager.getAllSchemes()
    assertThat(schemes.size(), equalTo(1))
    assertThat(schemes.get(0).data, equalTo(String(data)))
  }

  public Test fun loadSchemesWithTheSameNames() {
    val data = FileUtil.loadFileBytes(File(testDataPath, "local.xml"))
    val dirPath = "\$ROOT_CONFIG$/keymaps"
    save("$dirPath/local.xml", data)
    save("$dirPath/local2.xml", data)

    val schemesManager = createSchemesManager(dirPath)
    schemesManager.loadSchemes()
    val schemes = schemesManager.getAllSchemes()
    assertThat(schemes.size(), equalTo(1))
    assertThat(schemes.get(0).data, equalTo(String(data)))
  }
}