package com.intellij.ide.starter.bus

enum class EventTimelineState {
  /** Right before the action */
  READY,

  /** After action was completed */
  FINISHED
}