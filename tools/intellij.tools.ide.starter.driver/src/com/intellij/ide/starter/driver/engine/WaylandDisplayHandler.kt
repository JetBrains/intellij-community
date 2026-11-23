// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.driver.engine

import com.intellij.ide.starter.coroutine.CommonScope.scopeForProcesses
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private const val UNIX_FILE_TYPE_MASK = 0xF000
private const val UNIX_SOCKET_TYPE = 0xC000
private const val DEFAULT_DISPLAY_RESOLUTION = "1920x1080"
private val defaultStartupTimeout = 30.seconds
private val vncClientTimeout = 24.hours

/**
 * Runs [action] with the Mutter session environment when the tests enable Wayland.
 * With `-Dawt.toolkit.name=auto`, JBR selects WLToolkit if it can connect to the Wayland socket.
 */
@OptIn(LowLevelLocalMachineAccess::class)
internal suspend fun <T> withWaylandDisplayIfNecessary(
  logsDir: Path,
  action: suspend (Map<String, String>?) -> T,
): T {
  if (OS.CURRENT != OS.Linux || !System.getenv("USE_WAYLAND_DISPLAY").toBoolean()) return action(null)

  return WaylandDisplayHandler.withDisplay(logsDir, scopeForProcesses) { session ->
    session.processes.runWithHealthCheck { action(session.environment) }
  }
}

/** Starts a headless Mutter session for UI tests that set USE_WAYLAND_DISPLAY=true. */
internal object WaylandDisplayHandler {
  private val sessionMutex = Mutex()

  suspend fun <T> withDisplay(logsDir: Path, scope: CoroutineScope, action: suspend (WaylandSession) -> T): T = sessionMutex.withLock {
    val session = WaylandSession(System.getenv(), logsDir.createDirectories(), scope)
    try {
      session.processes.use {
        session.start()
        action(session)
      }
    }
    finally {
      session.deleteSessionData()
    }
  }
}

/** Runs the services that provide a Wayland display and virtual input devices. */
internal class WaylandSession(
  private val settings: Map<String, String>,
  private val logsDir: Path,
  scope: CoroutineScope,
) {
  private val runtimeDir = Path(requireSetting("XDG_RUNTIME_DIR")).toAbsolutePath()
  private val displayName = requireSetting("WAYLAND_DISPLAY")
  private val busAddress = requireSetting("DBUS_SESSION_BUS_ADDRESS")
  private val startupTimeout = settings["MUTTER_STARTUP_TIMEOUT_SECONDS"]?.toLong()?.seconds ?: defaultStartupTimeout
  private val displayResolution = settings["WAYLAND_DISPLAY_RESOLUTION"] ?: DEFAULT_DISPLAY_RESOLUTION
  private val vncPort = settings["GNOME_REMOTE_DESKTOP_VNC_PORT"] ?: "5900"

  val environment: Map<String, String> = mapOf(
    // Store the sockets for Wayland, D-Bus, and PipeWire in the session directory.
    "XDG_RUNTIME_DIR" to runtimeDir.toString(),
    // Connect the IDE to the Wayland socket that Mutter creates.
    "WAYLAND_DISPLAY" to displayName,
    // Share the session bus between the IDE and the GNOME services.
    "DBUS_SESSION_BUS_ADDRESS" to busAddress,
    // Use software rendering through Mesa.
    "LIBGL_ALWAYS_SOFTWARE" to "1",
    // Identify the session as Wayland to the applications.
    "XDG_SESSION_TYPE" to "wayland",
  )
  private val sessionDataDir = Files.createTempDirectory(runtimeDir.createDirectories(), "gnome-data-")
  // Clear the X11 display so the services use Wayland.
  // Keep the GNOME data, including the keyring, separate for each session.
  val processes = WaylandSessionProcesses(scope, settings.minus("DISPLAY") + environment +
                                                ("XDG_DATA_HOME" to sessionDataDir.toString()))

  @OptIn(ExperimentalPathApi::class)
  suspend fun deleteSessionData() {
    withContext(NonCancellable + Dispatchers.IO) {
      sessionDataDir.deleteRecursively()
    }
  }

  suspend fun start() {
    prepareRuntimeDirectory()
    startSessionBus()
    val vncPassword = createVncPassword()
    startSecretService(vncPassword)
    startPipeWire()
    startMutter()
    startRemoteDesktop(vncPassword)
    processes.checkAlive()
    logOutput("Headless Mutter and GNOME Remote Desktop are ready on $displayName; logs: $logsDir")
  }

  private fun requireSetting(name: String): String = requireNotNull(settings[name]?.takeIf { it.isNotBlank() }) {
    "$name must be set by the test environment"
  }

  private fun prepareRuntimeDirectory() {
    require(Regex("[1-9][0-9]*x[1-9][0-9]*").matches(displayResolution)) {
      "WAYLAND_DISPLAY_RESOLUTION must use the WIDTHxHEIGHT format with positive dimensions"
    }
    require(displayName == Path(displayName).fileName.toString()) { "WAYLAND_DISPLAY must be a socket name" }
    require(startupTimeout.isPositive()) { "MUTTER_STARTUP_TIMEOUT_SECONDS must be positive" }
    require(vncPort.toInt() in 1..65535) { "GNOME_REMOTE_DESKTOP_VNC_PORT must be a valid port" }
    runtimeDir.createDirectories()
    val uid = Files.getAttribute(Path("/proc/self"), "unix:uid")
    require(Files.getAttribute(runtimeDir, "unix:uid") == uid) {
      "$runtimeDir must belong to the user that runs Mutter and the tests"
    }
    Files.setPosixFilePermissions(runtimeDir, PosixFilePermissions.fromString("rwx------"))
    (runtimeDir / displayName).deleteIfExists()
    (runtimeDir / "$displayName.lock").deleteIfExists()
    (runtimeDir / "bus").deleteIfExists()
  }

  /** Creates the session bus shared by the GNOME services and the IDE. */
  private suspend fun startSessionBus() {
    startService("D-Bus", "DBUS_LOG_FILE", "dbus.log",
                 listOf("dbus-daemon", "--session", "--nofork", "--nopidfile", "--address=$busAddress"))
    awaitSocket("bus")
  }

  /** Starts the Secret Service that stores the VNC credentials for GNOME Remote Desktop. */
  private suspend fun startSecretService(password: String) {
    startService("Secret Service", "GNOME_KEYRING_LOG_FILE", "gnome-keyring.log",
                 listOf("gnome-keyring-daemon", "--foreground", "--components=secrets", "--unlock"), password.toByteArray())
    awaitBusName("org.freedesktop.secrets")
  }

  /** Starts PipeWire and WirePlumber for GNOME Remote Desktop. */
  private suspend fun startPipeWire() {
    startService("PipeWire", "PIPEWIRE_LOG_FILE", "pipewire.log", listOf("pipewire"))
    awaitSocket("pipewire-0")
    startService("WirePlumber", "WIREPLUMBER_LOG_FILE", "wireplumber.log", listOf("wireplumber"))
  }

  /** Creates the virtual monitor and exposes the remote desktop APIs. */
  private suspend fun startMutter() {
    startService("Mutter", "MUTTER_LOG_FILE", "mutter.log", listOf(
      "mutter", "--headless", "--wayland", "--no-x11",
      "--virtual-monitor=$displayResolution",
      "--wayland-display=$displayName", "--sm-disable",
    ))
    awaitSocket(displayName)
    awaitBusName("org.gnome.Mutter.RemoteDesktop")
    awaitBusName("org.gnome.Mutter.ScreenCast")
  }

  /** Keeps a local VNC connection open so Mutter retains its virtual keyboard and pointer. */
  private suspend fun startRemoteDesktop(password: String) {
    for (arguments in listOf(
      listOf("set-auth-method", "password"), listOf("disable-view-only"), listOf("set-password", password), listOf("enable"),
    )) {
      processes.runCommand(listOf("grdctl", "vnc") + arguments, startupTimeout, silent = true)
    }
    startService("GNOME Remote Desktop", "GNOME_REMOTE_DESKTOP_LOG_FILE", "gnome-remote-desktop.log",
                 listOf("/usr/libexec/gnome-remote-desktop-daemon", "--vnc-port", vncPort))
    processes.awaitReady("the VNC listener on port $vncPort", startupTimeout) {
      processes.runCommand(listOf("ss", "-Hltn", "sport = :$vncPort"), startupTimeout).isNotBlank()
    }
    val clientLog = logFile("VNC_CLIENT_LOG_FILE", "vnc-client.log")
    val clientTimeoutSeconds = vncClientTimeout.inWholeSeconds.toString()
    processes.start("Local VNC client", listOf(
      "vncdotool", "--server", "127.0.0.1::$vncPort", "--timeout", clientTimeoutSeconds, "--logfile", clientLog.toString(),
      "move", "1", "1", "key", "shift", "pause", clientTimeoutSeconds,
    ), clientLog, "$password\n".toByteArray())
    processes.awaitReady("the local VNC connection", startupTimeout) {
      processes.runCommand(listOf("ss", "-Htn", "state", "established",
                                  "( sport = :$vncPort or dport = :$vncPort )"), startupTimeout).isNotBlank()
    }
  }

  private suspend fun startService(name: String, logSetting: String, logName: String, args: List<String>, input: ByteArray = byteArrayOf()) {
    processes.start(name, args, logFile(logSetting, logName), input)
  }

  private fun logFile(setting: String, name: String): Path =
    settings[setting]?.let { Path(it) } ?: (logsDir / name)

  private suspend fun awaitSocket(name: String) {
    val socket = runtimeDir / name
    processes.awaitReady("the socket $socket", startupTimeout) {
      Files.exists(socket) && ((Files.getAttribute(socket, "unix:mode") as Int) and UNIX_FILE_TYPE_MASK) == UNIX_SOCKET_TYPE
    }
  }

  private suspend fun awaitBusName(name: String) {
    processes.runCommand(listOf("gdbus", "wait", "--session", "--timeout", startupTimeout.inWholeSeconds.toString(), name),
                         startupTimeout)
  }

  private fun createVncPassword(): String =
    ByteArray(4).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
}