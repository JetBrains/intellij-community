// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starter.utils.threadDumpParser


enum class ThreadOperation(name: String) {
  Socket("socket operation"), IO("I/O");

  private val myName: String?

  init {
    myName = name
  }

  override fun toString(): String {
    return myName!!
  }
}
