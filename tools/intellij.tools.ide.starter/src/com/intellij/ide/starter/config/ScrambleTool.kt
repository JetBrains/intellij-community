package com.intellij.ide.starter.config

import org.jetbrains.intellij.build.ScrambleTool

interface ScrambleToolProvider {
  fun get(): ScrambleTool? = null
}