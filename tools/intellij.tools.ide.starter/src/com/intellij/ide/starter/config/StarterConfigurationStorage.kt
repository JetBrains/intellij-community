package com.intellij.ide.starter.config

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.models.SystemBind
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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
private const val ENV_JBR_DEV_SERVER_VERSION = "JBR_DEV_SERVER_VERSION"
private const val ENABLE_SCRAMBLING_FOR_DEVSERVER = "ENABLE_SCRAMBLING_FOR_DEVSERVER"
private const val ENV_MONITORING_DUMPS_INTERVAL_SECONDS = "MONITORING_DUMPS_INTERVAL_SECONDS"
private const val ENV_COROUTINE_SCOPES_CANCEL_TIMEOUT_MS = "COROUTINE_SCOPES_CANCEL_TIMEOUT_MS"
private const val ENV_DEBUG_LOGGING_ENABLED = "DEBUG_LOGGING_ENABLED"

val starterConfigurationStorageDefaults = mapOf<String, String>(
  ENV_ENABLE_CLASS_FILE_VERIFICATION to System.getenv(ENV_ENABLE_CLASS_FILE_VERIFICATION),
  ENV_USE_LATEST_DOWNLOADED_IDE_BUILD to System.getenv(ENV_USE_LATEST_DOWNLOADED_IDE_BUILD),
  ENV_JUNIT_RUNNER_USE_INSTALLER to System.getenv(ENV_JUNIT_RUNNER_USE_INSTALLER),
  ENV_USE_DOCKER_CONTAINER to System.getenv(ENV_USE_DOCKER_CONTAINER),
  INSTALLER_INCLUDE_RUNTIME_MODULE_REPOSITORY to "false",
  IGNORED_TEST_FAILURE_PATTERN to System.getenv(IGNORED_TEST_FAILURE_PATTERN),
  AFTER_EACH_MESSAGE_BUS_CLEANUP to System.getenv().getOrDefault(AFTER_EACH_MESSAGE_BUS_CLEANUP, "false"),
  ENV_LOG_ENVIRONMENT_VARIABLES to CIServer.instance.isBuildRunningOnCI.toString(),
  SPLIT_MODE_ENABLED to System.getenv().getOrDefault("REMOTE_DEV_RUN", "false"),
  ENV_JBR_DEV_SERVER_VERSION to System.getenv(ENV_JBR_DEV_SERVER_VERSION),
  ENABLE_SCRAMBLING_FOR_DEVSERVER to System.getenv().getOrDefault("ENABLE_SCRAMBLING_FOR_DEVSERVER", "false"),
  ENV_MONITORING_DUMPS_INTERVAL_SECONDS to System.getenv().getOrDefault(ENV_MONITORING_DUMPS_INTERVAL_SECONDS, "60"),
  ENV_COROUTINE_SCOPES_CANCEL_TIMEOUT_MS to System.getenv().getOrDefault(ENV_COROUTINE_SCOPES_CANCEL_TIMEOUT_MS, "2000"),
  ENV_DEBUG_LOGGING_ENABLED to System.getenv().getOrDefault(ENV_DEBUG_LOGGING_ENABLED, "false"),
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
fun ConfigurationStorage.Companion.additionDockerBinds(): Set<SystemBind> = SystemBind.setFromString(instance().get(ENV_USE_DOCKER_ADDITIONAL_BINDS)
                                                                                                     ?: "")

/**
 *  Is it necessary to include [runtime module repository](psi_element://com.intellij.platform.runtime.repository) in the installed IDE?
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

fun ConfigurationStorage.Companion.monitoringDumpsIntervalSeconds(value: Int) = instance().put(ENV_MONITORING_DUMPS_INTERVAL_SECONDS, value.toString())
fun ConfigurationStorage.Companion.monitoringDumpsIntervalSeconds(): Int = instance().get(ENV_MONITORING_DUMPS_INTERVAL_SECONDS)?.toIntOrNull()
                                                                           ?: error("No value for $ENV_MONITORING_DUMPS_INTERVAL_SECONDS")

/**
 * By default, Message bus cleanup is performed after each test container run. This is needed to prevent side effects when once IDE run is used among several tests methods.
 * To prevent subscriptions of the current test affecting other tests in one test container, consider using `EventsBus.subscribeOnce` or unsubscribing.
 */
fun ConfigurationStorage.Companion.afterEachMessageBusCleanup() = instance().getBoolean(AFTER_EACH_MESSAGE_BUS_CLEANUP)
fun ConfigurationStorage.Companion.afterEachMessageBusCleanup(value: Boolean) = instance().put(AFTER_EACH_MESSAGE_BUS_CLEANUP, value)

/**
 * If ENV variable like `17.0.10b1171.14` otherwise the value will be read from `community/build/dependencies/dependencies.properties`
 */
fun ConfigurationStorage.Companion.jbrVersionForDevServer(): String? = instance().getOrNull(ENV_JBR_DEV_SERVER_VERSION)

fun ConfigurationStorage.Companion.isScramblingEnabled(): Boolean = instance().getBoolean(ENABLE_SCRAMBLING_FOR_DEVSERVER)

/**
 * To enable scrambling on TC, you have to have:
 * `jps.auth.spaceUsername` and `jps.auth.spacePassword` otherwise you will get error: `Credentials are missing, unable to download from`
 *
 * Note that enabling scrambling increase the run of dev server by 5 minutes tests might require bigger timeout.
 */
fun ConfigurationStorage.Companion.enableScrambling() = instance().put(ENABLE_SCRAMBLING_FOR_DEVSERVER, true)
fun ConfigurationStorage.Companion.disableScrambling() = instance().put(ENABLE_SCRAMBLING_FOR_DEVSERVER, false)

var ConfigurationStorage.Companion.coroutineScopesCancellationTimeout: Duration
  get() = instance().get(ENV_COROUTINE_SCOPES_CANCEL_TIMEOUT_MS) { (it ?: "2000").toLong().milliseconds }
  set(value) = instance().put(ENV_COROUTINE_SCOPES_CANCEL_TIMEOUT_MS, value.inWholeMilliseconds.toString())

var ConfigurationStorage.Companion.starterDebugEnabled: Boolean
  get() = instance().get(ENV_DEBUG_LOGGING_ENABLED).toBoolean()
  set(value) = instance().put(ENV_DEBUG_LOGGING_ENABLED, value)