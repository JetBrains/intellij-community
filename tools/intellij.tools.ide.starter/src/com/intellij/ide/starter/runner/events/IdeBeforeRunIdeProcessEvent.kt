package com.intellij.ide.starter.runner.events

import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.starter.bus.events.Event

class IdeBeforeRunIdeProcessEvent(val runContext: IDERunContext) : Event()