package com.intellij.ide.starter.driver.engine.remoteDev

import com.intellij.tools.ide.performanceTesting.commands.CommandChain

private const val CMD_PREFIX = '%'

fun <T : CommandChain> T.transportDelay(delayMs: Int): T = apply {
  addCommand("${CMD_PREFIX}transportDelay", "$delayMs")
}
