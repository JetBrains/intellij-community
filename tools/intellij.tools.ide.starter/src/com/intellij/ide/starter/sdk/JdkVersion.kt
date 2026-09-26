package com.intellij.ide.starter.sdk

enum class JdkVersion(val number: Int) {
  JDK_8(8),
  JDK_11(11),
  JDK_15(15),
  JDK_16(16),
  JDK_17(17),
  JDK_20(20),
  JDK_21(21),
  JDK_25(25);

  override fun toString(): String {
    return number.toString()
  }
}