package com.intellij.ide.starter.driver

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.step
import com.intellij.tools.ide.performanceTesting.commands.goto

fun Driver.goto(line: Int, column: Int) {
  step("Go to line $line, column $column") {
    execute { it.goto(line, column) }
  }
}
