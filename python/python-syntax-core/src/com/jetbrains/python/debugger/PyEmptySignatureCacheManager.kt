package com.jetbrains.python.debugger

import com.jetbrains.python.ast.PyAstFunction

/**
 * Holds no signature. It serves a process without the Python debugger: the JetBrains Client frontend, and JetBrains Light
 * before the smart mode.
 */
internal class PyEmptySignatureCacheManager : PySignatureCacheManager() {
  override fun recordSignature(signature: PySignature) = Unit
  override fun findParameterType(function: PyAstFunction, name: String): String? = null
  override fun findSignature(function: PyAstFunction): PySignature? = null
  override fun clearCache(): Boolean = false
}
