package com.intellij.ide.starter.config

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.models.SystemBind

private const val SPLIT_MODE_ENABLED = "SPLIT_MODE_ENABLED"
private const val ENV_ENABLE_CLASS_FILE_VERIFICATION = "ENABLE_CLASS_FILE_VERIFICATION"
private const val ENV_USE_LATEST_DOWNLOADED_IDE_BUILD = "USE_LATEST_DOWNLOADED_IDE_BUILD"
private const val ENV_JUNIT_RUNNER_USE_INSTALLER = "JUNIT_RUNNER_USE_INSTALLER"
private const val INSTALLER_INCLUDE_RUNTIME_MODULE_REPOSITORY = "INSTALLER_INCLUDE_RUNTIME_MODULE_REPOSITORY"
private const val ENV_LOG_ENVIRONMENT_VARIABLES = "LOG_ENVIRONMENT_VARIABLES"
private const val IGNORED_TEST_FAILURE_PATTERN = "IGNORED_TEST_FAILURE_PATTERN"
private const val ENV_USE_DOCKER_CONTAINER = "USE_DOCKER_CONTAINER"
private const val ENV_USE_DOCKER_ADDITIONAL_BINDS = "USE_DOCKER_ADDITIONAL_BINDS"
private const val AFTER_EACH_MESSAGE_BUS_CLEANUP = "AFTER_EACH_MESSAGE_BUS_CLEANUP"

val starterConfigurationStorageDefaults = mapOf<String, String>(
  ENV_ENABLE_CLASS_FILE_VERIFICATION to System.getenv(ENV_ENABLE_CLASS_FILE_VERIFICATION),
  ENV_USE_LATEST_DOWNLOADED_IDE_BUILD to System.getenv(ENV_USE_LATEST_DOWNLOADED_IDE_BUILD),
  ENV_JUNIT_RUNNER_USE_INSTALLER to System.getenv(ENV_JUNIT_RUNNER_USE_INSTALLER),
  ENV_USE_DOCKER_CONTAINER to System.getenv(ENV_USE_DOCKER_CONTAINER),
  INSTALLER_INCLUDE_RUNTIME_MODULE_REPOSITORY to "false",
  IGNORED_TEST_FAILURE_PATTERN to System.getenv(IGNORED_TEST_FAILURE_PATTERN),
  AFTER_EACH_MESSAGE_BUS_CLEANUP to System.getenv().getOrDefault(AFTER_EACH_MESSAGE_BUS_CLEANUP, "false"),
  ENV_LOG_ENVIRONMENT_VARIABLES to CIServer.instance.isBuildRunningOnCI.toString(),
  SPLIT_MODE_ENABLED to System.getenv().getOrDefault("REMOTE_DEV_RUN", "false")
).filter { entry ->
  @Suppress("SENSELESS_COMPARISON")
  entry.value != null
}

fun ConfigurationStorage.Companion.useLatestDownloadedIdeBuild() = instance().getBoolean(ENV_USE_LATEST_DOWNLOADED_IDE_BUILD)

fun ConfigurationStorage.Companion.useInstaller(): Boolean = instance().getBoolean(ENV_JUNIT_RUNNER_USE_INSTALLER)
fun ConfigurationStorage.Companion.useInstaller(value: Boolean) = instance().put(ENV_JUNIT_RUNNER_USE_INSTALLER, value)

fun ConfigurationStorage.Companion.useDockerContainer(): Boolean = instance().getBoolean(ENV_USE_DOCKER_CONTAINER)
fun ConfigurationStorage.Companion.useDockerContainer(value: Boolean) = instance().put(ENV_USE_DOCKER_CONTAINER, value)
fun ConfigurationStorage.Companion.setAdditionDockerBinds(value: Set<SystemBind>) = instance().put(ENV_USE_DOCKER_ADDITIONAL_BINDS, SystemBind.string(value))
fun ConfigurationStorage.Companion.additionDockerBinds(): Set<SystemBind> = SystemBind.setFromString(instance().get(ENV_USE_DOCKER_ADDITIONAL_BINDS)?:"")
/**
 *  Is it needed to include [runtime module repository](psi_element://com.intellij.platform.runtime.repository) in the installed IDE?
 */
fun ConfigurationStorage.Companion.includeRuntimeModuleRepositoryInIde(): Boolean = instance().getBoolean(INSTALLER_INCLUDE_RUNTIME_MODULE_REPOSITORY)
fun ConfigurationStorage.Companion.includeRuntimeModuleRepositoryInIde(value: Boolean) = instance().put(INSTALLER_INCLUDE_RUNTIME_MODULE_REPOSITORY, value)

/** Log env variables produced by [com.intellij.ide.starter.process.exec.ProcessExecutor] */
fun ConfigurationStorage.Companion.logEnvVariables() = instance().getBoolean(ENV_LOG_ENVIRONMENT_VARIABLES)

/**
 * This flag is supposed to be used only from the test framework/command handlers, not from tests themselves.
 * Tests should know nothing about the environment they are running in and only contain the test scenario.
 */
fun ConfigurationStorage.Companion.splitMode(): Boolean = instance().getBoolean(SPLIT_MODE_ENABLED)
fun ConfigurationStorage.Companion.splitMode(value: Boolean) = instance().put(SPLIT_MODE_ENABLED, value)

fun ConfigurationStorage.Companion.classFileVerification(): Boolean = instance().getBoolean(ENV_ENABLE_CLASS_FILE_VERIFICATION)
fun ConfigurationStorage.Companion.classFileVerification(value: Boolean) = instance().put(ENV_ENABLE_CLASS_FILE_VERIFICATION, value)

fun ConfigurationStorage.Companion.logEnvironmentVariables(): Boolean = instance().getBoolean(ENV_LOG_ENVIRONMENT_VARIABLES)
fun ConfigurationStorage.Companion.logEnvironmentVariables(value: Boolean) = instance().put(ENV_LOG_ENVIRONMENT_VARIABLES, value)

fun ConfigurationStorage.Companion.useLastDownloadedBuild(): Boolean = instance().getBoolean(ENV_USE_LATEST_DOWNLOADED_IDE_BUILD)
fun ConfigurationStorage.Companion.useLastDownloadedBuild(value: Boolean) = instance().put(ENV_USE_LATEST_DOWNLOADED_IDE_BUILD, value)

fun ConfigurationStorage.Companion.ignoredTestFailuresPattern(pattern: String) = instance().put(IGNORED_TEST_FAILURE_PATTERN, pattern)
fun ConfigurationStorage.Companion.ignoredTestFailuresPattern(): String? = instance().get(IGNORED_TEST_FAILURE_PATTERN)

/**
 * By default, Message bus cleanup is performed after each test container run. This is needed to prevent side effects when once IDE run is used among several tests methods.
 * To prevent subscriptions of the current test affecting other tests in one test container, consider using `EventsBus.subscribeOnce` or unsubscribing.
 */
fun ConfigurationStorage.Companion.afterEachMessageBusCleanup() = instance().getBoolean(AFTER_EACH_MESSAGE_BUS_CLEANUP)
fun ConfigurationStorage.Companion.afterEachMessageBusCleanup(value: Boolean) = instance().put(AFTER_EACH_MESSAGE_BUS_CLEANUP, value)