package com.intellij.jupyter.ui.test.util.utils

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.getRegistry

fun Driver.setRegistry(key: String, value: Boolean) {
  val registry = getRegistry(key)
  registry.setValue(value)
}
