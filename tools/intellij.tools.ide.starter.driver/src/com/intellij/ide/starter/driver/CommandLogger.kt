package com.intellij.ide.starter.driver

import com.intellij.driver.client.Remote

@Remote("com.jetbrains.performancePlugin.CommandLogger", plugin = "com.jetbrains.performancePlugin")
interface CommandLogger