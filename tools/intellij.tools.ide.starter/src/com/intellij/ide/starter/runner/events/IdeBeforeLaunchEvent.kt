package com.intellij.ide.starter.runner.events

import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.starter.bus.events.Event

class IdeBeforeLaunchEvent(val runContext: IDERunContext) : Event()