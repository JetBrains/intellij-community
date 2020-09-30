package com.intellij.space.plugins.pipelines.services.execution

import circlet.pipelines.engine.Ticker

class SystemTimeTicker : Ticker {
  override val transactionTime: Long get() = System.currentTimeMillis()
}
