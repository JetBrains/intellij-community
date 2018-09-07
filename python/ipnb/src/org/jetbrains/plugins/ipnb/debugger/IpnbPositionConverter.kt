// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.debugger

import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XSourcePosition
import com.jetbrains.python.debugger.PyLocalPositionConverter
import com.jetbrains.python.debugger.PySourcePosition

class IpnbPositionConverter(var project: Project) : PyLocalPositionConverter() {

  private fun getCellPositionBySourcePosition(position: XSourcePosition): CellPosition {
    // TODO: return cell start for position
    val startCellPosition = createXSourcePosition(position.file, 0)
    if (startCellPosition != null) {
      return CellPosition(startCellPosition)
    }
    else {
      return CellPosition(position)
    }
  }

  override fun create(filePath: String, line: Int): PySourcePosition {
    val cellId: Int
    try {
      cellId = filePath.toInt()
    }
    catch (e: NumberFormatException) {
      return super.create(filePath, line)
    }

    val cellIdManager = IpnbCellIdManagerImpl.getInstance(project)
    val cellPosition = cellIdManager.getCellPositionById(cellId)

    return super.create(cellPosition.start.file.path, cellPosition.start.line + line + 1)
  }

  override fun convertToPython(position: XSourcePosition): PySourcePosition {
    val cellPosition = getCellPositionBySourcePosition(position)

    val cellIdManager = IpnbCellIdManagerImpl.getInstance(project)
    val cellId = cellIdManager.getIdByPosition(cellPosition)

    return IpnbSourcePosition(cellId, position.line - cellPosition.start.line)
  }

  override fun convertFromPython(position: PySourcePosition, frameName: String): XSourcePosition? {
    val cellId: Int
    try {
      cellId = position.file.toInt()
    }
    catch (e: NumberFormatException) {
      return createXSourcePosition(getVirtualFile(position.file), position.line)
    }

    val cellIdManager = IpnbCellIdManagerImpl.getInstance(project)
    val cellPosition = cellIdManager.getCellPositionById(cellId)

    return createXSourcePosition(cellPosition.start.file, cellPosition.start.line + position.line)
  }


}