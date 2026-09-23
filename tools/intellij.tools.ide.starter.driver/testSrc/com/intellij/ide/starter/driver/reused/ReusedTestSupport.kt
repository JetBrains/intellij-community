package com.intellij.ide.starter.driver.reused

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import java.nio.file.Path
import kotlin.io.path.createDirectories

internal val reusedDriverTestIdeInfo = IdeInfo(
  productCode = "IU",
  platformPrefix = "idea",
  executableFileName = "idea",
  fullName = "IntelliJ IDEA",
)

fun cachedProjectConfiguration(
  projectDir: Path,
  useRelease: Boolean = false,
  additionalSetup: IDETestContext.() -> Unit = {},
): CachedProjectConfiguration {
  val testCase = TestCase(
    ideInfo = reusedDriverTestIdeInfo,
    projectInfo =
      LocalProjectInfo(
        projectDir = projectDir.createDirectories(),
        isReusable = true,
      ),
  ).let { if (useRelease) it.useRelease() else it }
  return CachedProjectConfiguration(
    testCase = testCase,
    setup = {
      applyVMOptionsPatch { addSystemProperty("idea.trust.all.projects", true) }
      additionalSetup()
      this
    },
  )
}
