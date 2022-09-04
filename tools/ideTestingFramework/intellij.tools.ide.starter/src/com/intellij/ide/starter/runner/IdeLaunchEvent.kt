package com.intellij.ide.starter.runner

import com.intellij.ide.starter.bus.Event
import com.intellij.ide.starter.bus.EventState

class IdeLaunchEvent(state: EventState, runContext: IDERunContext) : Event<IDERunContext>(state, runContext)