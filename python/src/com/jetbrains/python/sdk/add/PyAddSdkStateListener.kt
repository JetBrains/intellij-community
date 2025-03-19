// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add

interface PyAddSdkStateListener {
  fun onComponentChanged()

  fun onActionsStateChanged()
}