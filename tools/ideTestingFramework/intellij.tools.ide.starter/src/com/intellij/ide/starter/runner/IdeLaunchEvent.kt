package com.intellij.ide.starter.runner

import com.intellij.ide.starter.bus.Event
import com.intellij.ide.starter.bus.EventState

data class IdeLaunchEventData(val runContext: IDERunContext, val ideProcess: Process?)

class IdeLaunchEvent(state: EventState, data: IdeLaunchEventData) : Event<IdeLaunchEventData>(state, data)