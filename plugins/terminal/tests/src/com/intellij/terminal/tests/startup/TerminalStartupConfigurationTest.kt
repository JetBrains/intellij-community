package com.intellij.terminal.tests.startup

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslPath
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelPathBoundDescriptor
import com.intellij.platform.eel.fs.EelFileSystemApi.CreateTemporaryEntryOptions
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.isMac
import com.intellij.platform.eel.isPosix
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.EelType
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.setValueInTest
import com.intellij.terminal.tests.reworked.util.withShellIntegration
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.replaceService
import com.intellij.util.io.delete
import org.assertj.core.api.Assertions
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner.LOGIN_CLI_OPTION
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import org.jetbrains.plugins.terminal.runner.LocalShellIntegrationInjector.IJ_ZSH_DIR
import org.jetbrains.plugins.terminal.runner.LocalShellIntegrationInjector.ZDOTDIR
import org.jetbrains.plugins.terminal.runner.LocalTerminalStartCommandBuilder.INTERACTIVE_CLI_OPTION
import org.jetbrains.plugins.terminal.startup.TerminalEnvironmentVariablesProvider
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.jetbrains.plugins.terminal.util.ShellIntegration
import org.jetbrains.plugins.terminal.util.ShellType
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedClass
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Run with `intellij.idea.ultimate.tests.main` classpath to test in WSL/Docker, like on CI.
 *
 * If ijent binaries are missing locally (typically on macOS), refer to IJPL-197291 for resolution.
 */
@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS, OS.LINUX, OS.MAC])
@ParameterizedClass
internal class TerminalStartupConfigurationTest(private val eelHolder: EelHolder) {

  private val project: Project by projectFixture()

  @TestDisposable
  private lateinit var testDisposable: Disposable

  private val tempDir: Path by tempPathFixture()

  private val eelApi: EelApi
    get() = eelHolder.eel

  @TestFactory
  fun `bash on Unix`() = withShellIntegration(TIMEOUT) { allowShellIntegration ->
    Assumptions.assumeTrue(eelApi.descriptor.osFamily.isPosix)
    configureStartupOptionsAndValidateResult(InitialOptions(
      tempDir,
      "bash",
      allowShellIntegration,
    ), ConfiguredOptions(
      tempDir.asEelPath(eelApi.descriptor),
      expectedConfiguredBashCommand(listOf("bash"), allowShellIntegration),
      ShellIntegration(ShellType.BASH, true).takeIf { allowShellIntegration }
    ))
  }

  @TestFactory
  fun `zsh on Unix`() = withShellIntegration(TIMEOUT) { allowShellIntegration ->
    Assumptions.assumeTrue(eelApi.descriptor.osFamily.isPosix)
    configureStartupOptionsAndValidateResult(InitialOptions(
      tempDir,
      "/bin/zsh",
      allowShellIntegration,
    ), ConfiguredOptions(
      tempDir.asEelPath(eelApi.descriptor),
      expectedConfiguredZshCommand(listOf("/bin/zsh")),
      ShellIntegration(ShellType.ZSH, true).takeIf { allowShellIntegration }
    ))
  }

  @TestFactory
  fun `powershell on Windows`() = withShellIntegration(TIMEOUT) { allowShellIntegration ->
    Assumptions.assumeTrue(OS.current() == OS.WINDOWS)
    configureStartupOptionsAndValidateResult(InitialOptions(
      tempDir,
      "powershell.exe",
      allowShellIntegration,
    ), ConfiguredOptions(
      tempDir.asEelPath(LocalEelDescriptor),
      expectedConfiguredPowerShellCommand(listOf("powershell.exe"), allowShellIntegration),
      ShellIntegration(ShellType.POWERSHELL, expectedCommandBlocks(LocalEelDescriptor)).takeIf { allowShellIntegration }
    ))
  }

  @TestFactory
  fun `convert wsl_exe to Linux command (WSL filesystem)`() = withShellIntegration(TIMEOUT) { allowShellIntegration ->
    Assumptions.assumeTrue(eelHolder.type == EelType.Wsl)
    val distribName = WslPath.parseWindowsUncPath(tempDir.pathString)!!.distributionId
    val bashInWsl = "/my/custom/bin/bash"
    registerShellForDetection(bashInWsl)
    configureStartupOptionsAndValidateResult(InitialOptions(
      tempDir,
      "wsl.exe -d $distribName",
      allowShellIntegration,
    ), ConfiguredOptions(
      tempDir.asEelPath(eelApi.descriptor),
      expectedConfiguredBashCommand(listOf(bashInWsl), allowShellIntegration),
      ShellIntegration(ShellType.BASH, expectedCommandBlocks(eelApi.descriptor)).takeIf { allowShellIntegration },
    ))
  }

  @TestFactory
  fun `convert wsl_exe to Linux command (Windows drive mounted in WSL)`() = withShellIntegration(TIMEOUT) { allowShellIntegration ->
    Assumptions.assumeTrue(eelHolder.type == EelType.Wsl)
    val distribName = WslPath.parseWindowsUncPath(tempDir.pathString)!!.distributionId
    val defaultShellInWsl = "/my/custom/bin/bash"
    registerShellForDetection(defaultShellInWsl)
    val windowsTmpDir = createTempDirectory(localEel, testDisposable)
    configureStartupOptionsAndValidateResult(InitialOptions(
      windowsTmpDir,
      "wsl --distribution $distribName",
      allowShellIntegration,
    ), ConfiguredOptions(
      EelPath.parse(WSLDistribution(distribName).getWslPath(windowsTmpDir)!!, eelApi.descriptor),
      expectedConfiguredBashCommand(listOf(defaultShellInWsl), allowShellIntegration),
      ShellIntegration(ShellType.BASH, expectedCommandBlocks(eelApi.descriptor)).takeIf { allowShellIntegration },
    ))
  }

  @TestFactory
  fun `LocalTerminalCustomizer is not applied when shell process is local and working directory is WSL`(
  ) = withShellIntegration(TIMEOUT) { allowShellIntegration ->
    Assumptions.assumeTrue(eelHolder.type == EelType.Wsl)
    var called = false
    register(localTerminalCustomizer { called = true })

    configureStartupOptionsAndValidateResult(InitialOptions(
      tempDir,
      "powershell.exe",
      allowShellIntegration,
    ), ConfiguredOptions(
      tempDir.asEelPath(LocalEelDescriptor),
      expectedConfiguredPowerShellCommand(listOf("powershell.exe"), allowShellIntegration),
      ShellIntegration(ShellType.POWERSHELL, expectedCommandBlocks(LocalEelDescriptor)).takeIf { allowShellIntegration }
    ))

    // workingDir is a WSL path, but powershell.exe is launched as a local process
    // LocalTerminalCustomizer shouldn't be called in such case.
    Assertions.assertThat(called).isFalse
  }

  @TestFactory
  fun `LocalTerminalCustomizer is not applied when shell process is WSL and working directory is local`(
  ) = withShellIntegration(TIMEOUT) { allowShellIntegration ->
    Assumptions.assumeTrue(eelHolder.type == EelType.Wsl)
    var called = false
    register(localTerminalCustomizer { called = true })

    val distribName = WslPath.parseWindowsUncPath(tempDir.pathString)!!.distributionId
    val defaultShellInWsl = "/my/custom/bin/zsh"
    registerShellForDetection(defaultShellInWsl)

    val windowsTmpDir = createTempDirectory(localEel, testDisposable)
    configureStartupOptionsAndValidateResult(InitialOptions(
      windowsTmpDir,
      "wsl.exe -d $distribName",
      allowShellIntegration,
    ), ConfiguredOptions(
      EelPath.parse(WSLDistribution(distribName).getWslPath(windowsTmpDir)!!, eelApi.descriptor),
      expectedConfiguredZshCommand(listOf(defaultShellInWsl)),
      ShellIntegration(ShellType.ZSH, expectedCommandBlocks(eelApi.descriptor)).takeIf { allowShellIntegration }
    ))

    // workingDir is on local Windows drive, but Zsh is launched in WSL via IJEnt
    // LocalTerminalCustomizer shouldn't be called in such case.
    Assertions.assertThat(called).isFalse
  }

  private fun register(
    vararg customizers: org.jetbrains.plugins.terminal.LocalTerminalCustomizer // FQN to avoid importing deprecated class
  ) {
    @Suppress("DEPRECATION")
    ExtensionTestUtil.maskExtensions(
      org.jetbrains.plugins.terminal.LocalTerminalCustomizer.EP_NAME,
      customizers.toList(),
      testDisposable
    )
  }

  @Suppress("DEPRECATION")
  private fun localTerminalCustomizer(handler: (envs: MutableMap<String, String>) -> Unit): org.jetbrains.plugins.terminal.LocalTerminalCustomizer {
    return object : org.jetbrains.plugins.terminal.LocalTerminalCustomizer() {
      @Deprecated("Deprecated in Java")
      override fun customizeCommandAndEnvironment(
        project: Project,
        workingDirectory: String?,
        shellCommand: List<String>,
        envs: MutableMap<String, String>,
        eelDescriptor: EelDescriptor,
      ): List<String> {
        handler(envs)
        return shellCommand
      }
    }
  }

  private fun registerShellForDetection(@Suppress("SameParameterValue") shellPath: String) {
    val provider = object: TerminalEnvironmentVariablesProvider {
      override suspend fun fetchMinimalEnvironmentVariableValue(eelApi: EelApi, envName: String): String {
        if (envName == "SHELL") {
          return shellPath
        }
        throw IllegalStateException("Unexpected envName: $envName")
      }
    }
    ApplicationManager.getApplication().replaceService(TerminalEnvironmentVariablesProvider::class.java, provider, testDisposable)
  }

  private fun configureStartupOptionsAndValidateResult(
    initial: InitialOptions,
    expected: ConfiguredOptions,
  ) {
    Assertions.assertThat(project.getEelDescriptor()).isEqualTo(eelApi.descriptor)
    val runner = object : LocalTerminalDirectRunner(project) {
      override fun isGenTwoTerminalEnabled(): Boolean = true
    }
    val projectOptionsProvider = TerminalProjectOptionsProvider.getInstance(project)
    TerminalOptionsProvider.instance::shellIntegration.setValueInTest(initial.allowShellIntegration, testDisposable)
    projectOptionsProvider::startingDirectory.setValueInTest(initial.workingDirectory.toString(), testDisposable)
    projectOptionsProvider::shellPath.setValueInTest(initial.shellCommand, testDisposable)

    val baseOptions = ShellStartupOptions.Builder().processType(TerminalProcessType.SHELL).build()
    val resultOptions = runner.configureStartupOptions(baseOptions)

    assertEelDescriptorsEqual(resultOptions.workingDirectoryEelPathNotNull.descriptor, expected.workingDirectory.descriptor)
    Assertions.assertThat(resultOptions.workingDirectoryEelPathNotNull).isEqualTo(expected.workingDirectory)

    val expectedShellCommand = expected.shellCommand.replacePlaceholder(resultOptions.shellCommand.orEmpty())
    Assertions.assertThat(resultOptions.shellCommand).isEqualTo(expectedShellCommand)

    Assertions.assertThat(resultOptions.shellIntegration).isEqualTo(expected.shellIntegration)
    Assertions.assertThat(resultOptions.envVariables["INTELLIJ_TERMINAL_COMMAND_BLOCKS_REWORKED"]).isEqualTo(
      "1".takeIf { expected.shellIntegration?.commandBlocks == true }
    )
    if (expected.shellIntegration?.shellType == ShellType.ZSH) {
      Assertions.assertThat(resultOptions.envVariables).containsKey(ZDOTDIR)
      Assertions.assertThat(resultOptions.envVariables).containsKey(IJ_ZSH_DIR)
    }
  }

  private fun assertEelDescriptorsEqual(actual: EelDescriptor, expected: EelDescriptor) {
    // EelDescriptors pointing to the same WSL might be unequal. This will fail:
    // Assertions.assertThat(actual).isEqualTo(expected)
    when (expected) {
      is LocalEelDescriptor -> {
        Assertions.assertThat(actual).isEqualTo(expected)
      }
      is EelPathBoundDescriptor -> {
        Assertions.assertThat(actual.name).isEqualTo(expected.name)
        actual as EelPathBoundDescriptor
        Assertions.assertThat(actual.rootPath).isEqualTo(expected.rootPath)
      }
      else -> {
        Assertions.fail("Unexpected EelDescriptor: ${expected.javaClass.name}")
      }
    }
  }

  private fun expectedConfiguredBashCommand(
    initialBashCommand: List<String>,
    allowShellIntegration: Boolean,
  ): List<String> {
    return buildList {
      addAll(initialBashCommand)
      if (allowShellIntegration) {
        addAll(listOf("--rcfile", PATH_TO_SHELL_INTEGRATION_PLACEHOLDER))
      }
      if (!allowShellIntegration && (eelApi.platform.isMac || eelHolder.type in listOf(EelType.Wsl, EelType.Docker))) {
        add(LOGIN_CLI_OPTION)
      }
      add(INTERACTIVE_CLI_OPTION)
    }
  }

  private fun expectedConfiguredZshCommand(
    initialZshCommand: List<String>,
  ): List<String> {
    return buildList {
      addAll(initialZshCommand)
      if (eelApi.platform.isMac || eelHolder.type in listOf(EelType.Wsl, EelType.Docker)) {
        add(LOGIN_CLI_OPTION)
      }
      add(INTERACTIVE_CLI_OPTION)
    }
  }

  private fun expectedConfiguredPowerShellCommand(
    initialPowerShellCommand: List<String>,
    allowShellIntegration: Boolean,
  ): List<String> {
    return buildList {
      addAll(initialPowerShellCommand)
      if (allowShellIntegration) {
        addAll(listOf("-NoExit", "-ExecutionPolicy", "Bypass", "-File", PATH_TO_SHELL_INTEGRATION_PLACEHOLDER))
      }
    }
  }

}

private fun List<String>.replacePlaceholder(placeholderSource: List<String>): List<String> {
  return this.mapIndexed { index, value ->
    if (value == PATH_TO_SHELL_INTEGRATION_PLACEHOLDER && index < placeholderSource.size)
      placeholderSource[index]
    else
      value
  }
}

private data class InitialOptions(
  val workingDirectory: Path,
  val shellCommand: String,
  val allowShellIntegration: Boolean,
)

private data class ConfiguredOptions(
  val workingDirectory: EelPath,
  val shellCommand: List<String>,
  val shellIntegration: ShellIntegration?,
)

private fun expectedCommandBlocks(eelDescriptor: EelDescriptor): Boolean {
  // similar to LocalShellIntegrationInjector.isSystemCompatibleWithCommandBlocks
  // but adapted to the buildserver where CI agents have recent Windows 10
  return eelDescriptor.osFamily.isPosix ||
         System.getProperty("os.name") !in listOf("Windows Server 2016", "Windows Server 2019")
}

private suspend fun createTempDirectory(
  eelApi: EelApi,
  parentDisposable: Disposable,
  prefix: String = TerminalStartupConfigurationTest::class.java.simpleName,
): Path {
  val options = CreateTemporaryEntryOptions.Builder().prefix(prefix).build()
  val tempDir = eelApi.fs.createTemporaryDirectory(options).getOrThrow().asNioPath()
  Disposer.register(parentDisposable) {
    try {
      tempDir.delete(recursively = true)
    }
    catch (e: IOException) {
      fileLogger().warn("Can't delete $tempDir", e)
    }
  }
  return tempDir
}

private val TIMEOUT: Duration = 60.seconds
private const val PATH_TO_SHELL_INTEGRATION_PLACEHOLDER: String = "<path to IntelliJ shell integration>"
