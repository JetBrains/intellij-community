package com.intellij.tools.ide.performanceTesting.commands

interface MarshallableCommand {
  fun storeToString(): String
}
