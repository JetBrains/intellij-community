package org.jetbrains.settingsRepository.test

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.options.SchemeManagerImpl
import com.intellij.options.TestScheme
import com.intellij.options.TestSchemesProcessor
import com.intellij.options.serialize
import com.intellij.options.toByteArray
import org.eclipse.jgit.lib.Repository
import org.hamcrest.CoreMatchers.equalTo
import org.jetbrains.settingsRepository.ReadonlySource
import org.jetbrains.settingsRepository.getPluginSystemDir
import org.jetbrains.settingsRepository.git.cloneBare
import org.jetbrains.settingsRepository.git.commit
import org.jetbrains.settingsRepository.icsManager
import org.junit.Assert.assertThat
import org.junit.Test
import java.io.File

class LoadTest : TestCase() {
  private fun createSchemesManager(dirPath: String): SchemeManagerImpl<TestScheme, TestScheme> {
    return SchemeManagerImpl<TestScheme, TestScheme>(dirPath, TestSchemesProcessor(), RoamingType.PER_USER, getProvider(), tempDirManager.newDirectory())
  }

  public Test fun `load scheme`() {
    val localScheme = TestScheme("local")
    val data = localScheme.serialize().toByteArray()
    val dirPath = "\$ROOT_CONFIG$/keymaps"
    save("$dirPath/local.xml", data)

    val schemesManager = createSchemesManager(dirPath)
    schemesManager.loadSchemes()
    assertThat(schemesManager.getAllSchemes(), equalTo(listOf(localScheme)))
  }

  public Test fun `load scheme with the same names`() {
    val localScheme = TestScheme("local")
    val data = localScheme.serialize().toByteArray()
    val dirPath = "\$ROOT_CONFIG$/keymaps"
    save("$dirPath/local.xml", data)
    save("$dirPath/local2.xml", data)

    val schemesManager = createSchemesManager(dirPath)
    schemesManager.loadSchemes()
    assertThat(schemesManager.getAllSchemes(), equalTo(listOf(localScheme)))
  }

  public Test fun `load scheme from repo and read-only repo`() {
    val localScheme = TestScheme("local")

    val dirPath = "\$ROOT_CONFIG$/keymaps"
    save("$dirPath/local.xml", localScheme.serialize().toByteArray())

    val remoteScheme = TestScheme("remote")
    val remoteRepository = createRepository()
    remoteRepository
      .add(remoteScheme.serialize().toByteArray(), "$dirPath/Mac OS X from RubyMine.xml")
      .commit("")

    remoteRepository.useAsReadOnlySource {
      val schemesManager = createSchemesManager(dirPath)
      schemesManager.loadSchemes()
      assertThat(schemesManager.getAllSchemes(), equalTo(listOf(remoteScheme, localScheme)))
      assertThat(schemesManager.isMetadataEditable(localScheme), equalTo(true))
      assertThat(schemesManager.isMetadataEditable(remoteScheme), equalTo(false))
    }
  }

  public Test fun `scheme overrides read-only`() {
    val schemeName = "Emacs"
    val localScheme = TestScheme(schemeName, "local")

    val dirPath = "\$ROOT_CONFIG$/keymaps"
    save("$dirPath/$schemeName.xml", localScheme.serialize().toByteArray())

    val remoteScheme = TestScheme(schemeName, "remote")
    val remoteRepository = createRepository()
    remoteRepository
      .add(remoteScheme.serialize().toByteArray(), "$dirPath/$schemeName.xml")
      .commit("")

    remoteRepository.useAsReadOnlySource {
      val schemesManager = createSchemesManager(dirPath)
      schemesManager.loadSchemes()
      assertThat(schemesManager.getAllSchemes(), equalTo(listOf(localScheme)))
      assertThat(schemesManager.isMetadataEditable(localScheme), equalTo(false))
    }
  }
}

inline fun Repository.useAsReadOnlySource(runnable: () -> Unit) {
  createAndRegisterReadOnlySource()
  try {
    runnable()
  }
  finally {
    icsManager.readOnlySourcesManager.setSources(emptyList())
  }
}

fun Repository.createAndRegisterReadOnlySource(): ReadonlySource {
  val source = ReadonlySource(getWorkTree().getAbsolutePath())
  assertThat(cloneBare(source.url!!, File(getPluginSystemDir(), source.path!!)).getObjectDatabase().exists(), equalTo(true))
  icsManager.readOnlySourcesManager.setSources(listOf(source))
  return source
}