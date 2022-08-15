package com.intellij.ide.starter.sdk

enum class JdkVersion(val number: Int) {
  JDK_8(8),
  JDK_11(11),
  JDK_15(15),
  JDK_17(17);

  override fun toString(): String {
    return number.toString()
  }
}