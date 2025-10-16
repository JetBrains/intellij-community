package com.intellij.ide.starter.di

import org.kodein.di.DI

object DISnapshot {
  private lateinit var diSnapshot: DI

  fun initSnapshot(value: DI) {
    if (!this::diSnapshot.isInitialized) diSnapshot = value
  }

  fun get(): DI = diSnapshot
}