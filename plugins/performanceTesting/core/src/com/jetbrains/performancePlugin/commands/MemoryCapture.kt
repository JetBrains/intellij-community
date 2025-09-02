// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import java.lang.management.ManagementFactory

class MemoryCapture(
  val usedMb: Long,
  val maxMb: Long,
  val metaspaceMb: Long
) {
  companion object {
    @JvmStatic
    fun capture(): MemoryCapture {
      val runtime = Runtime.getRuntime()

      val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
      val maxMb = runtime.maxMemory() / 1024 / 1024

      var metaspaceMb = 0L
      for (memoryMXBean in ManagementFactory.getMemoryPoolMXBeans()) {
        if ("Metaspace" == memoryMXBean.name) {
          metaspaceMb = memoryMXBean.usage.used / 1024 / 1024
          break
        }
      }

      return MemoryCapture(usedMb, maxMb, metaspaceMb)
    }
  }
}