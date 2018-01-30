// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add.wizard

enum class WizardControlAction {
  PREVIOUS, NEXT, FINISH, OK;

  fun enabled() = this.to(that = true)
  fun disabled() = this.to(that = false)
}