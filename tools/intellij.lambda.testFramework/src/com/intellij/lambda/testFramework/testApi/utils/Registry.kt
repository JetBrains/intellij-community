package com.intellij.lambda.testFramework.testApi.utils

import com.intellij.openapi.util.registry.Registry
import com.intellij.remoteDev.tests.LambdaIdeContext

inline fun <T> withRegistryValue(name: String, value: Boolean, func: () -> T): T {
  val feature = Registry.get(name)
  val oldValue = feature.asBoolean()
  return try {
    feature.setValue(value)
    func()
  }
  finally {
    feature.setValue(oldValue)
  }
}

inline fun <T> withRegistryValue(name: String, value: String, func: () -> T): T {
  val feature = Registry.get(name)
  val oldValue = feature.asString()
  return try {
    feature.setValue(value)
    func()
  }
  finally {
    feature.setValue(oldValue)
  }
}

/**
 * Is not necessary to clean it up
 */
context(lambdaIdeContext: LambdaIdeContext)
fun setRegistryValue(name: String, value: Boolean) {
  Registry.get(name).setValue(value)
}
