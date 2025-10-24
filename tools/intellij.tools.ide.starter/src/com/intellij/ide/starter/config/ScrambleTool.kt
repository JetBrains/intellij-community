package com.intellij.ide.starter.config

interface ScrambleToolProvider {
  /**
   * The actual return type is org.jetbrains.intellij.build.ScrambleTool
   * But direct dependency on intellij.platform.buildScripts is impossible in Starter
   * */
  fun get(): Any? = null
}