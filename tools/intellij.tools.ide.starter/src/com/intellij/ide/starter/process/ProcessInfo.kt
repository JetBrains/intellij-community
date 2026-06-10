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
  val argumentsProvider: () -> List<String>,
  private val startTime: Instant?,
  private val user: String?,
  val processHandle: ProcessHandle? = null,
  private val portThatIsUsedByProcess: Int? = null,
) {

  val arguments: List<String> by lazy { argumentsProvider() }

  companion object {
    suspend fun create(pid: Long, portThatIsUsedByProcess: Int? = null): ProcessInfo {
      val internal = getOrCreate(pid)
      return ProcessInfo(
        pid = pid,
        parentPid = internal.parentPid,
        name = internal.name,
        argumentsProvider = { internal.arguments },
        startTime = internal.startTime,
        user = internal.user,
        processHandle = internal.processHandle,
        portThatIsUsedByProcess = portThatIsUsedByProcess
      )
    }

    fun create(p: OSProcess, portThatIsUsedByProcess: Int? = null): ProcessInfo = ProcessInfo(
      pid = p.processID.toLong(),
      parentPid = p.parentProcessID.toLong(),
      name = p.name ?: "Not Available",
      argumentsProvider = { p.arguments ?: emptyList() },
      startTime = p.startTime.let(Instant::ofEpochMilli),
      user = p.user,
      processHandle = ProcessHandle.of(p.processID.toLong()).getOrNull(),
      portThatIsUsedByProcess = portThatIsUsedByProcess
    )

    fun OSProcess.toProcessInfo(portThatIsUsedByProcess: Int? = null): ProcessInfo {
      return create(this, portThatIsUsedByProcess)
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
            val osProcess: OSProcess? by lazy {
              SystemInfo().operatingSystem.getProcess(pid.toInt())
            }

            ProcessInfo(
              pid = pid,
              parentPid = osProcess?.parentProcessID?.toLong(),
              name = osProcess?.name ?: "Not Available",
              argumentsProvider = { osProcess?.arguments ?: emptyList() },
              startTime = osProcess?.startTime?.let(Instant::ofEpochMilli),
              user = osProcess?.user,
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

  val description: String by lazy {
    buildString {
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
}