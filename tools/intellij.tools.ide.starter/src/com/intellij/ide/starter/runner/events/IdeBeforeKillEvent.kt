package com.intellij.ide.starter.runner.events

import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.starter.bus.events.Event


class IdeBeforeKillEvent(val runContext: IDERunContext,
                         val ideProcess: Process,
                         val ideProcessId: Long) : Event()