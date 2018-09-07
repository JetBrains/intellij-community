// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.debugger

import com.intellij.xdebugger.XSourcePosition
import com.jetbrains.python.debugger.PySourcePosition

// represents position in Jupyter notebook on Python Side: file - cellId, line - line inside cell
class IpnbSourcePosition(cellId: Int, line: Int) : PySourcePosition(cellId.toString(), line)

// represents cell's start position
data class CellPosition(val start: XSourcePosition)