// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.configuration

import java.util.function.Consumer

interface PyInterpreterModeNotifier {
  fun addInterpreterModeListener(listener: Consumer<Boolean>)

  fun isRemoteSelected(): Boolean
}