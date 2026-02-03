package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.buildTool.BuildToolType
import com.intellij.ide.starter.buildTool.GradleBuildTool
import com.intellij.ide.starter.buildTool.MavenBuildTool
import com.intellij.ide.starter.frameworks.AndroidFramework
import com.intellij.ide.starter.frameworks.SpringFramework
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.junit5.config.KillOutdatedProcessesAfterEach
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.utils.hyphenateTestName
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Path


@ExtendWith(MockitoExtension::class)
@ExtendWith(KillOutdatedProcessesAfterEach::class)
class FrameworksInjectionTest {
  @TempDir
  lateinit var testDirectory: Path

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private lateinit var testCase: TestCase<*>

  @Mock
  private lateinit var ide: InstalledIde

  @Test
  fun `framework and build tools injection should work`() {
    val testName = object {}.javaClass.enclosingMethod.name.hyphenateTestName()
    val paths = IDEDataPaths.createPaths<IDEDataPaths>(testName, testDirectory, useInMemoryFs = false)

    val projectHome = testCase.projectInfo.downloadAndUnpackProject()
    val context = IDETestContext(paths = paths,
                                 ide = ide,
                                 testCase = testCase,
                                 testName = testName,
                                 _resolvedProjectHome = projectHome)

    val spring = context.withFramework<SpringFramework>()
    spring.testContext.shouldBe(context)

    val android = context.withFramework<AndroidFramework>()
    android.testContext.shouldBe(context)

    val maven = context.withBuildTool<MavenBuildTool>()
    maven.temporaryMavenM3RepoPath
    maven.testContext.shouldBe(context)

    val gradle = context.withBuildTool<GradleBuildTool>()
    gradle.type.shouldBe(BuildToolType.GRADLE)
  }
}