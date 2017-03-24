package com.intellij.debugger.streams.ui

interface LinkedValuesMapping {
  fun getLinkedValues(value: ValueWithPosition): List<ValueWithPosition>?
}