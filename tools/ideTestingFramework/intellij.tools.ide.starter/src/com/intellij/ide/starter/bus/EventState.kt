// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.bus

enum class EventState {
  UNDEFINED,

  /** Right before the action */
  BEFORE,

  /** After the action was completed */
  AFTER
}