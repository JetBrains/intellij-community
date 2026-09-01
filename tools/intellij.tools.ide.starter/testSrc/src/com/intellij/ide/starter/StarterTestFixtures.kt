package com.intellij.ide.starter

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeInfoType
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.project.NoProject
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.PlatformUtils
import org.jdom.Element
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * A context with a mocked IDE, for a test of a method that only writes a config file. A [projectPath] of `null` gives
 * a context with [NoProject].
 */
fun starterTestContext(
  testHome: Path,
  testName: String,
  projectPath: Path? = null,
  productCode: String = IdeInfoType.IDEA_ULTIMATE.productCode,
  isFrontend: Boolean = false,
): IDETestContext {
  val ide = mock(InstalledIde::class.java)
  doReturn(productCode).`when`(ide).productCode
  val ideInfo = IdeInfo(
    productCode = productCode,
    platformPrefix = if (isFrontend) PlatformUtils.JETBRAINS_CLIENT_PREFIX else PlatformUtils.IDEA_PREFIX,
    baseIdePlatformPrefixForFrontend = if (isFrontend) PlatformUtils.IDEA_PREFIX else null,
    executableFileName = "idea",
    fullName = "IDEA",
  )
  return IDETestContext(
    paths = IDEDataPaths(testHome = testHome, inMemoryRoot = null),
    ide = ide,
    testCase = TestCase(ideInfo = ideInfo, projectInfo = projectPath?.let { LocalProjectInfo(it) } ?: NoProject),
    testName = testName,
    _resolvedProjectHome = projectPath,
    publishers = emptyList(),
  )
}

/** The `value` of the option [option] of the component [component] in the config file [file]. */
fun optionValue(file: Path, component: String, option: String): String? =
  optionElement(file, component, option)?.getAttributeValue("value")

/** The `option` element [option] of the component [component], or `null` when the file holds none. */
fun optionElement(file: Path, component: String, option: String): Element? =
  componentElement(file, component)?.getChildren("option")?.firstOrNull { it.getAttributeValue("name") == option }

/** The `component` element [component] of the config file [file], or `null` when there is none. */
fun componentElement(file: Path, component: String): Element? {
  if (!file.exists()) return null
  return JDOMUtil.load(file).getChildren("component").firstOrNull { it.getAttributeValue("name") == component }
}
