package com.intellij.ide.starter.utils

data class FindUsagesCallParameters(val pathToFile: String, val element: String) {
  override fun toString() = "$pathToFile $element)"
}