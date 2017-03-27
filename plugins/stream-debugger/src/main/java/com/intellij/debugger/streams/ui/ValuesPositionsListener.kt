package com.intellij.debugger.streams.ui

import java.util.*

/**
 * @author Vitaliy.Bibaev
 */
interface ValuesPositionsListener : EventListener {
  fun valuesPositionsChanged(): Unit
}