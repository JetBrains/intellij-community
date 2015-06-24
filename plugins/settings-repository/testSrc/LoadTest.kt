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
import org.jetbrains.jgit.dirCache.AddFile
import org.jetbrains.jgit.dirCache.edit
import org.jetbrains.settingsRepository.ReadonlySource
import org.jetbrains.settingsRepository.getPluginSystemDir
import org.jetbrains.settingsRepository.git.cloneBare
import org.jetbrains.settingsRepository.git.commit
import org.jetbrains.settingsRepository.icsManager
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

  public Test fun `load scheme`() {
    val data = FileUtil.loadFileBytes(File(testDataPath, "local.xml"))
    val dirPath = "\$ROOT_CONFIG$/keymaps"
    save("$dirPath/local.xml", data)

    val schemesManager = createSchemesManager(dirPath)

    schemesManager.loadSchemes()
    val schemes = schemesManager.getAllSchemes()
    assertThat(schemes.size(), equalTo(1))
    assertThat(schemes.get(0).data, equalTo(String(data)))
  }

  public Test fun `load scheme with the same names`() {
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

  public Test fun `load scheme from repo and read-only repo`() {
    val data = FileUtil.loadFileBytes(File(testDataPath, "local.xml"))
    val dirPath = "\$ROOT_CONFIG$/keymaps"
    save("$dirPath/local.xml", data)

    val remoteRepository = getRemoteRepository()
    val workTree: File = remoteRepository.getWorkTree()
    val filePath = "\$ROOT_CONFIG$/keymaps/Mac OS X from RubyMine.xml"
    val file = File(testDataPath, "remote.xml")
    FileUtil.copy(file, File(workTree, filePath))
    remoteRepository.edit(AddFile(filePath))
    remoteRepository.commit("")

    val source = ReadonlySource(remoteRepository.getWorkTree().getAbsolutePath())
    val readOnlyRepository = cloneBare(source.url!!, File(getPluginSystemDir(), source.path!!))
    assertThat(readOnlyRepository.getObjectDatabase().exists(), equalTo(true))

    icsManager.readOnlySourcesManager.setSources(listOf(source))
    try {
      val schemesManager = createSchemesManager(dirPath)

      schemesManager.loadSchemes()
      val schemes = schemesManager.getAllSchemes()
      assertThat(schemes.size(), equalTo(2))
//      assertThat(schemes.get(0).data, equalTo(String(data)))
    }
    finally {
      icsManager.readOnlySourcesManager.setSources(emptyList())
    }
  }
}