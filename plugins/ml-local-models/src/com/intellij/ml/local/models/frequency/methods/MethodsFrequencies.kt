package com.intellij.ml.local.models.frequency.methods

class MethodsFrequencies(private var totalFrequency: Int = 0,
                         private val methods: MutableMap<Int, Int> = mutableMapOf()) {
  companion object {
    fun withUsage(methodName: String): MethodsFrequencies {
      return MethodsFrequencies(1, mutableMapOf(methodName.hashCode() to 1))
    }
  }

  fun addUsage(name: String) {
    totalFrequency++
    val hash = name.hashCode()
    methods[hash] = methods.getOrDefault(hash, 0) + 1
  }

  fun merge(another: MethodsFrequencies): MethodsFrequencies {
    totalFrequency += another.totalFrequency
    for (method in another.methods) {
      methods[method.key] = methods.getOrDefault(method.key, 0) + method.value
    }
    return this
  }

  fun getMethodFrequency(methodName: String): Int = methods[methodName.hashCode()] ?: 0

  fun getTotalFrequency(): Int = totalFrequency

  fun getMethods(): Map<Int, Int> = methods
}