package com.intellij.ide.starter.di

import org.kodein.di.DI

object DISnapshot {
  private lateinit var diSnapshot: DI

  fun initSnapshot(value: DI, overwrite: Boolean = false) {
    if (!this::diSnapshot.isInitialized || overwrite) diSnapshot = value
  }

  fun get(): DI = diSnapshot
}