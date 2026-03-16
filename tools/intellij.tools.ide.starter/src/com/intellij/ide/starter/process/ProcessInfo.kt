package com.intellij.ide.starter.process

import oshi.SystemInfo
import oshi.software.os.OSProcess
import java.time.Instant
import kotlin.jvm.optionals.getOrNull

class ProcessInfo private constructor(
  val pid: Long,
  val name: String,
  val arguments: List<String>,
  private val startTime: Instant?,
  private val user: String?,
  val processHandle: ProcessHandle? = null,
  private val portThatIsUsedByProcess: Int? = null,
) {

  companion object {
    fun create(pid: Long, portThatIsUsedByProcess: Int? = null): ProcessInfo {
      val opProcess = SystemInfo().operatingSystem.getProcess(pid.toInt()) // null if the process doesn't exist
      if (opProcess == null) {
        return ProcessInfo(pid, "Not Available", emptyList(), null, null, null, portThatIsUsedByProcess)
      }
      else {
        return opProcess.toProcessInfo(portThatIsUsedByProcess)
      }
    }

    fun OSProcess.toProcessInfo(portThatIsUsedByProcess: Int? = null): ProcessInfo {
      return ProcessInfo(pid = processID.toLong(),
                         name = name,
                         arguments = arguments,
                         startTime = Instant.ofEpochMilli(startTime),
                         user = user,
                         processHandle = ProcessHandle.of(processID.toLong()).getOrNull(),
                         portThatIsUsedByProcess = portThatIsUsedByProcess)
    }

    fun Process.toProcessInfo(): ProcessInfo {
      return create(pid())
    }
  }

  override fun equals(other: Any?): Boolean {
    if (other !is ProcessInfo) return false
    return pid == other.pid
  }

  override fun hashCode(): Int = pid.hashCode()

  override fun toString(): String = "$pid $name"

  val description: String = buildString {
    appendLine("PID: $pid")
    if (portThatIsUsedByProcess != null) {
      appendLine("Port that is used by a process: $portThatIsUsedByProcess")
    }
    appendLine("Name: $name")
    appendLine("Arguments: $arguments")
    appendLine("Start time: $startTime")
    appendLine("User: $user")
  }
}