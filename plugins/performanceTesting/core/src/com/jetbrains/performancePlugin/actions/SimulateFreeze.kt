// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

internal abstract class SimulateFreezeBase : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  @Suppress("HardCodedStringLiteral")
  override fun actionPerformed(e: AnActionEvent) {
    val durationString = Messages.showInputDialog(
      e.project,
      "Enter freeze duration in ms",
      "Freeze Simulator",
      null,
      "",
      object : InputValidator {
        override fun checkInput(inputString: String?): Boolean = StringUtil.parseInt(inputString, -1) > 0
        override fun canClose(inputString: String?): Boolean = StringUtil.parseInt(inputString, -1) > 0
      }) ?: return
    simulatedFreeze(e.coroutineScope, durationString.toLong())
  }

  protected abstract fun simulatedFreeze(scope: CoroutineScope, ms: Long)
}

internal class SimulateFreeze : SimulateFreezeBase() {
  override fun simulatedFreeze(scope: CoroutineScope, ms: Long) {
    Thread.sleep(ms)
  }
}

internal class SimulateRWFreeze : SimulateFreezeBase() {
  override fun simulatedFreeze(scope: CoroutineScope, ms: Long) {
    val semaphore = Semaphore(1, 1)

    scope.launch {
      val counter = AtomicInteger(0)
      readAction {
        semaphore.release()

        if (counter.incrementAndGet() > 1) { // invoke the block only once
          return@readAction
        }

        logger<SimulateRWFreeze>().info("slow read-action is started")

        // emulate slow stuff
        Thread.sleep(ms)

        ProgressManager.checkCanceled()

        logger<SimulateRWFreeze>().info("slow read-action is NOT canceled! It MUST have been!")
      }
    }

    scope.launch {
      semaphore.acquire()
      logger<SimulateRWFreeze>().info("start write-action")
      withContext(Dispatchers.EDT) {
        application.runWriteAction {}
      }
    }
  }
}