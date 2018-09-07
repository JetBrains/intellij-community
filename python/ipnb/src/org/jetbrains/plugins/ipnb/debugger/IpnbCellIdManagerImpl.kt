// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.debugger

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.jetbrains.python.debugger.PyDebuggerException

class IpnbCellIdManagerImpl {

  private var idToPosition: MutableMap<Int, CellPosition> = mutableMapOf()
  private var positionToId: MutableMap<CellPosition, Int> = mutableMapOf()

  init {
    idToPosition = mutableMapOf()
    positionToId = mutableMapOf()
  }

  fun updatePositions() {
    // TODO: implement position update after change
  }

  fun getCellPositionById(cellId: Int): CellPosition {
    val position = idToPosition[cellId]
    if (position == null) {
      throw PyDebuggerException("Position for cell id $cellId wasn't found")
    }
    return position
  }

  private fun generateId(cellPosition: CellPosition): Int {
    // temporary workaround
    return 239
  }

  fun getIdByPosition(cellPosition: CellPosition): Int {
    var cellId = positionToId.get(cellPosition)
    if (cellId == null) {
      cellId = generateId(cellPosition)
      positionToId[cellPosition] = cellId
      idToPosition[cellId] = cellPosition
    }
    return cellId
  }

  companion object {
    fun getInstance(project: Project): IpnbCellIdManagerImpl {
      return ServiceManager.getService(project, IpnbCellIdManagerImpl::class.java)
    }
  }

}