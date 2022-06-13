package com.intellij.ide.starter.bus

/** Event, that works as a marker, that some action happened */
class Signal<T>(val state: EventTimelineState)