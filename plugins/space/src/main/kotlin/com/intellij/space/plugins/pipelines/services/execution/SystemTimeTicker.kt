// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.plugins.pipelines.services.execution

import circlet.pipelines.engine.Ticker

class SystemTimeTicker : Ticker {
  override val transactionTime: Long get() = System.currentTimeMillis()
}
