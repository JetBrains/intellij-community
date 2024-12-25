package com.intellij.python.junit5Tests.framework

import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.statistics.InterpreterTarget
import com.jetbrains.python.statistics.InterpreterType

val MockInterpreterStatisticsInfo = InterpreterStatisticsInfo(
  InterpreterType.PYENV,
  InterpreterTarget.LOCAL,
  false,
  false,
  false,
  false
)