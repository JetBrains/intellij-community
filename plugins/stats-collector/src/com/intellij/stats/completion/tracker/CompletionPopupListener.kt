// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.stats.completion.tracker

interface CompletionPopupListener {
    fun beforeDownPressed()
    fun downPressed()
    fun beforeUpPressed()
    fun upPressed()
    fun beforeBackspacePressed()
    fun afterBackspacePressed()
    fun beforeCharTyped(c: Char)

    companion object {
      val DISABLED: CompletionPopupListener = object : CompletionPopupListener {
        override fun beforeDownPressed(): Unit = Unit
        override fun downPressed(): Unit = Unit
        override fun beforeUpPressed(): Unit = Unit
        override fun upPressed(): Unit = Unit
        override fun afterBackspacePressed(): Unit = Unit
        override fun beforeBackspacePressed(): Unit = Unit
        override fun beforeCharTyped(c: Char): Unit = Unit
      }
    }
}