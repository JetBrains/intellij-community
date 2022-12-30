package com.intellij.ide.starter.bus

enum class EventState {
  UNDEFINED,

  /** Right before the action */
  BEFORE,

  /** After the action was completed */
  AFTER,

  /** When action started */
  IN_TIME
}