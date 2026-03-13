package com.intellij.ide.starter.process

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import oshi.SystemInfo
import oshi.software.os.OSProcess
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull

class ProcessInfo private constructor(
  val pid: Long,
  val parentPid: Long?,
  val name: String,
  val arguments: List<String>,
  private val startTime: Instant?,
  private val user: String?,
  val processHandle: ProcessHandle? = null,
  private val portThatIsUsedByProcess: Int? = null,
) {

  companion object {
    suspend fun create(pid: Long, portThatIsUsedByProcess: Int? = null): ProcessInfo {
      val internal = getOrCreate(pid)
      return ProcessInfo(
        pid = pid,
        parentPid = internal.parentPid,
        name = internal.name,
        arguments = internal.arguments,
        startTime = internal.startTime,
        user = internal.user,
        processHandle = internal.processHandle,
        portThatIsUsedByProcess = portThatIsUsedByProcess
      )
    }

    suspend fun OSProcess.toProcessInfo(portThatIsUsedByProcess: Int? = null): ProcessInfo {
      return create(processID.toLong(), portThatIsUsedByProcess)
    }

    suspend fun Process.toProcessInfo(): ProcessInfo {
      return create(pid())
    }

    /**
     * If you want to put Caffeine or any other fancy library here, stop and read this comment first.
     *
     * This class always calls this function inside a loop of every process.
     * Suppose there was something dumb like an LRU cache.
     * When the maximum size of the cache is less than the number of processes,
     * by the end of the loop the cache would start invalidating processes added at the beginning of the loop.
     * The next invocation of the loop would fetch earlier invalidated processes again.
     * So, effectively, in scenarios where we rely on the cache the most, there would always be cache misses for 100% requests.
     *
     * Adding some time limit for cache values doesn't make sense
     * because there are always processes that die in a millisecond and processes that live days.
     * It's unclear what timeout to choose.
     * A timeout for cache values would barely introduce any benefit but certainly would introduce non-deterministic behavior.
     */
    private val cache = ConcurrentHashMap<Long, Deferred<ProcessInfo>>()

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private suspend fun getOrCreate(pid: Long): ProcessInfo =
      cache.compute(pid) { _, old ->
        if (
          true == old?.run {
            isActive || isCompleted && getCompletionExceptionOrNull() == null && getCompleted().processHandle?.isAlive == true
          }
        ) {
          old
        }
        else {
          GlobalScope.async(Dispatchers.IO) {
            // null if the process doesn't exist
            val opProcess: OSProcess? by lazy {
              SystemInfo().operatingSystem.getProcess(pid.toInt())
            }

            ProcessInfo(
              pid = pid,
              parentPid = opProcess?.parentProcessID?.toLong(),
              name = opProcess?.name ?: "Not Available",
              arguments = opProcess?.arguments ?: emptyList(),
              startTime = opProcess?.startTime?.let(Instant::ofEpochMilli),
              user = opProcess?.user,
              processHandle = ProcessHandle.of(pid).getOrNull(),
              portThatIsUsedByProcess = null,
            )
          }
        }
      }!!.await()
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