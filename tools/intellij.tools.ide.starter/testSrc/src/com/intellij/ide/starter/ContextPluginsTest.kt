package com.intellij.ide.starter

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.runner.DevBuildServerRunner
import com.intellij.ide.starter.runner.NoOpDevBuildServerRunner
import com.intellij.ide.starter.runner.TestContainer
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.mockito.Mockito.doReturn
import java.nio.file.Path

class ContextPluginsTest {
  @TempDir
  lateinit var testHome: Path

  @Test
  fun `an IDE from sources loads each context plugin through plugin path`() {
    val context = starterTestContext(testHome, "context-plugins")
    val vmOptions = VMOptions(ide = context.ide, data = emptyList(), env = emptyMap())
    doReturn(true).`when`(context.ide).isFromSources
    doReturn(vmOptions).`when`(context.ide).vmOptions
    val pluginDir = testHome.resolve("pool/devkit")
    val requested = ArrayList<IdeInfo>()

    withRunner(object : DevBuildServerRunner by NoOpDevBuildServerRunner {
      override fun contextPluginPaths(ideInfo: IdeInfo): List<Path> {
        requested.add(ideInfo)
        return listOf(pluginDir)
      }
    }) {
      TestContainer.applyContextPlugins(context, context.testCase.ideInfo)
    }

    requested.map { it.productCode } shouldBe listOf(context.testCase.ideInfo.productCode)
    vmOptions.pluginPaths() shouldBe listOf(pluginDir.toAbsolutePath())
  }

  @Test
  fun `an IDE that is not from sources gets no plugin path`() {
    val context = starterTestContext(testHome, "no-context-plugins")
    val ide: InstalledIde = context.ide
    val vmOptions = VMOptions(ide = ide, data = emptyList(), env = emptyMap())
    doReturn(vmOptions).`when`(ide).vmOptions

    withRunner(object : DevBuildServerRunner by NoOpDevBuildServerRunner {
      override fun contextPluginPaths(ideInfo: IdeInfo): List<Path> = error("an installer IDE must not ask for context plugins")
    }) {
      TestContainer.applyContextPlugins(context, context.testCase.ideInfo)
    }

    vmOptions.pluginPaths().shouldBeEmpty()
  }

  private fun withRunner(runner: DevBuildServerRunner, action: () -> Unit) {
    val previousDi = di
    di = DI {
      extend(previousDi)
      bindSingleton<DevBuildServerRunner>(overrides = true) { runner }
    }
    try {
      action()
    }
    finally {
      di = previousDi
    }
  }
}
