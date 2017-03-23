package com.intellij.debugger.streams.ui

/**
 * @author Vitaliy.Bibaev
 */
interface MappedValuePositionProvider : ValuePositionProvider {
  fun getNextValues(value: ValueWithPosition): List<ValueWithPosition>
}