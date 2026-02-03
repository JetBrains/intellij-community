package com.intellij.ide.starter.runner.events

import com.intellij.tools.ide.starter.bus.shared.events.SharedEvent

class IdeExceptionEvent(val message: String) : SharedEvent()