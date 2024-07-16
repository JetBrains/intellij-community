package com.jetbrains.performancePlugin.events

import com.intellij.tools.ide.starter.bus.shared.events.SharedEvent


class StopProfilerEvent(val data: List<String>) : SharedEvent()