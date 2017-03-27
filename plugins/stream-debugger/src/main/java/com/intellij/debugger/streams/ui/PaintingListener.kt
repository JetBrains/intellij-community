package com.intellij.debugger.streams.ui

import java.util.*

/**
 * @author Vitaliy.Bibaev
 */
interface PaintingListener : EventListener {
  fun componentPainted(): Unit
}