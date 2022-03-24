package com.intellij.ide.starter.tests.unit

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.InstalledIDE
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.IDEDataPaths
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.kodein.di.direct
import org.kodein.di.instance
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Path


@ExtendWith(MockitoExtension::class)
class PluginsInjectionTest {

  @TempDir
  lateinit var testDirectory: Path

  @Mock
  private lateinit var testCase: TestCase

  @Mock
  private lateinit var ide: InstalledIDE

  @Test
  fun theSameIDETestContextShouldBeReferencedInPluginConfigurator() {
    val testName = "example test"
    val paths = IDEDataPaths.createPaths(testName, testDirectory, useInMemoryFs = false)

    val projectHome = testCase.projectInfo?.resolveProjectHome()
    val context = IDETestContext(paths = paths,
                                 ide = ide,
                                 testCase = testCase,
                                 testName = testName,
                                 _resolvedProjectHome = projectHome,
                                 patchVMOptions = { this },
                                 ciServer = di.direct.instance())

    context.pluginConfigurator.testContext.shouldBe(context)
  }
}