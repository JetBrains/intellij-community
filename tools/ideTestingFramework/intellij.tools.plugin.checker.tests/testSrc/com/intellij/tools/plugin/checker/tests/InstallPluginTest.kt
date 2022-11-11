package com.intellij.tools.plugin.checker.tests

import com.intellij.ide.starter.community.model.BuildType
import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.ide.starter.junit5.JUnit5StarterAssistant
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.runner.TestContainerImpl
import com.intellij.tools.plugin.checker.data.TestCases
import com.intellij.tools.plugin.checker.marketplace.MarketPlaceEvent
import com.jetbrains.performancePlugin.commands.chain.exitApp
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.TimeUnit

private const val MARKETPLACE_RAW_EVENT_DATA_ENV = "MARKETPLACE_RAW_EVENT_DATA"

@ExtendWith(JUnit5StarterAssistant::class)
class InstallPluginTest {

  private lateinit var testInfo: TestInfo
  private lateinit var context: TestContainerImpl

  companion object {
    val marketplaceRawEventData: String by lazy { System.getenv(MARKETPLACE_RAW_EVENT_DATA_ENV) }

    fun deserializeMessageFromMarketplace(input: String): MarketPlaceEvent {
      // TODO: implement deserialization, when real data will be available
      return MarketPlaceEvent(
        id = 1,
        file = "http://path-to-plugin-file.zip",
        productCode = "IU",
        productVersion = "IU-221.2.54",
        productLink = "http://link-to-download-ide",
        productType = "release", //release, eap, rc
        s3Path = "",
        forced = false
      )
    }

    @JvmStatic
    fun data(): List<EventToTestCaseParams> {
      val originalParams: EventToTestCaseParams = EventToTestCaseParams(
        event = deserializeMessageFromMarketplace(marketplaceRawEventData),
        testCase = TestCases.IU.GradleJitPackSimple
      )

      return listOf(modifyTestCaseForIdeVersion(originalParams))
    }

    fun modifyTestCaseForIdeVersion(params: EventToTestCaseParams): EventToTestCaseParams {
      val testCase = when (params.event.productType) {
        BuildType.EAP.type -> params.testCase.useEAP(params.event.productVersion)
        BuildType.RELEASE.type -> params.testCase.useRelease(params.event.productVersion)
        else -> TODO("Build type `${params.event.productType}` is not supported")
      }

      return params.copy(testCase = testCase)
    }
  }

  @ParameterizedTest
  @MethodSource("data")
  @Timeout(value = 15, unit = TimeUnit.MINUTES)
  fun installPluginTest(params: EventToTestCaseParams) {

    val testContext = context
      .initializeTestContext(testName = testInfo.hyphenateWithClass(), testCase = params.testCase)
      .prepareProjectCleanImport()
      .setSharedIndexesDownload(enable = true)
      .apply {
        pluginConfigurator.setupPluginFromURL(params.event.file)
      }

    testContext.runIDE(commands = CommandChain().exitApp())
  }
}