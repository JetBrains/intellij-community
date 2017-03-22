package com.intellij.debugger.streams.ui

/**
 * @author Vitaliy.Bibaev
 */
interface MappedValuePositionProvider : ValuePositionProvider {
  fun getMapToNextByValue(value: ValueWithPosition): List<ValueWithPosition>
}