package com.intellij.tools.ide.starter.bus.shared.events

import com.intellij.tools.ide.starter.bus.events.Event
import java.io.Serializable

/**
 * Very simple shared event-indicator, that something happened.
 * Make sure!
 * Each descendant of this class must be serializable
 */
open class SharedEvent : Event(), Serializable
