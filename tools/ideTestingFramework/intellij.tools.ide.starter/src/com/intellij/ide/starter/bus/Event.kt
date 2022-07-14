package com.intellij.ide.starter.bus

open class Event<T>(
  state: EventState = EventState.UNDEFINED,
  val data: T
) : Signal(state)