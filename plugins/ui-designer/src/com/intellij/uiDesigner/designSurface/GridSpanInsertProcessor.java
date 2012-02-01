/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadAbstractGridLayoutManager;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class GridSpanInsertProcessor {
  private final RadContainer myContainer;
  private final RadAbstractGridLayoutManager myLayoutManager;
  private final int myRow;
  private final int myColumn;
  private GridInsertMode myMode;
  private RadComponent myInsertCellComponent;

  public GridSpanInsertProcessor(RadContainer container,
                                 int insertRow,
                                 int insertColumn,
                                 GridInsertMode mode,
                                 ComponentDragObject dragObject) {
    myContainer = container;
    myLayoutManager = container.getGridLayoutManager();
    myRow = insertRow;
    myColumn = insertColumn;

    int[] vGridLines = myLayoutManager.getVerticalGridLines(container);
    int[] hGridLines = myLayoutManager.getHorizontalGridLines(container);

    RadComponent component = RadAbstractGridLayoutManager.getComponentAtGrid(container, insertRow, insertColumn);

    if (component != null) {
      int lastColIndex = insertColumn + dragObject.getColSpan(0);
      if (lastColIndex > vGridLines.length - 1) {
        lastColIndex = insertColumn + 1;
      }

      int lastRowIndex = insertRow + dragObject.getRowSpan(0);
      if (lastRowIndex > hGridLines.length - 1) {
        lastRowIndex = insertRow + 1;
      }

      Rectangle bounds = component.getBounds();
      bounds.translate(-vGridLines[insertColumn], -hGridLines[insertRow]);

      int spaceToRight = vGridLines[lastColIndex] - vGridLines[insertColumn] - (bounds.x + bounds.width);
      int spaceBelow = hGridLines[lastRowIndex] - hGridLines[insertRow] - (bounds.y + bounds.height);

      if (mode == GridInsertMode.RowBefore && bounds.y > GridInsertLocation.INSERT_RECT_MIN_SIZE) {
        myMode = GridInsertMode.RowBefore;
        myInsertCellComponent = component;
      }
      else if (mode == GridInsertMode.RowAfter && spaceBelow > GridInsertLocation.INSERT_RECT_MIN_SIZE) {
        myMode = GridInsertMode.RowAfter;
      }
      else if (mode == GridInsertMode.ColumnBefore && bounds.x > GridInsertLocation.INSERT_RECT_MIN_SIZE) {
        myMode = GridInsertMode.ColumnBefore;
        myInsertCellComponent = component;
      }
      else if (mode == GridInsertMode.ColumnAfter && spaceToRight > GridInsertLocation.INSERT_RECT_MIN_SIZE) {
        myMode = GridInsertMode.ColumnAfter;
      }
    }
  }

  public void doBefore(int newCell) {
    if (myMode == GridInsertMode.RowBefore) {
      int oldRow = myInsertCellComponent.getConstraints().getRow();
      int columns = myLayoutManager.getGridColumnCount(myContainer);
      for (int i = 0; i < columns; i++) {
        if (i != myColumn) {
          RadComponent component = RadAbstractGridLayoutManager.getComponentAtGrid(myContainer, oldRow, i);
          if (component != null) {
            GridConstraints constraints = component.getConstraints();

            if (constraints.getRow() == oldRow) {
              GridConstraints oldConstraints = (GridConstraints)constraints.clone();
              constraints.setRow(newCell);
              constraints.setRowSpan(constraints.getRowSpan() + oldRow - newCell);
              component.fireConstraintsChanged(oldConstraints);
            }

            i = constraints.getColumn() + constraints.getColSpan() - 1;
          }
        }
      }
    }
    else if (myMode == GridInsertMode.ColumnBefore) {
      int oldColumn = myInsertCellComponent.getConstraints().getColumn();
      int rows = myLayoutManager.getGridRowCount(myContainer);
      for (int i = 0; i < rows; i++) {
        if (i != myRow) {
          RadComponent component = RadAbstractGridLayoutManager.getComponentAtGrid(myContainer, i, oldColumn);
          if (component != null) {
            GridConstraints constraints = component.getConstraints();

            if (constraints.getColumn() == oldColumn) {
              GridConstraints oldConstraints = (GridConstraints)constraints.clone();
              constraints.setColumn(newCell);
              constraints.setColSpan(constraints.getColSpan() + oldColumn - newCell);
              component.fireConstraintsChanged(oldConstraints);
            }

            i = constraints.getRow() + constraints.getRowSpan() - 1;
          }
        }
      }
    }
  }

  public void doAfter(int newCell) {
    if (myMode == GridInsertMode.RowAfter) {
      int columns = myLayoutManager.getGridColumnCount(myContainer);
      for (int i = 0; i < columns; i++) {
        if (i != myColumn) {
          RadComponent component = RadAbstractGridLayoutManager.getComponentAtGrid(myContainer, myRow, i);
          if (component != null) {
            GridConstraints constraints = component.getConstraints();
            int endRow = constraints.getRow() + constraints.getRowSpan() - 1;

            if (endRow == myRow) {
              GridConstraints oldConstraints = (GridConstraints)constraints.clone();
              constraints.setRowSpan(constraints.getRowSpan() + newCell - myRow);
              component.fireConstraintsChanged(oldConstraints);
            }

            i = constraints.getColumn() + constraints.getColSpan() - 1;
          }
        }
      }
    }
    else if (myMode == GridInsertMode.ColumnAfter) {
      int rows = myLayoutManager.getGridRowCount(myContainer);
      for (int i = 0; i < rows; i++) {
        if (i != myRow) {
          RadComponent component = RadAbstractGridLayoutManager.getComponentAtGrid(myContainer, i, myColumn);
          if (component != null) {
            GridConstraints constraints = component.getConstraints();
            int endColumn = constraints.getColumn() + constraints.getColSpan() - 1;

            if (endColumn == myColumn) {
              GridConstraints oldConstraints = (GridConstraints)constraints.clone();
              constraints.setColSpan(constraints.getColSpan() + newCell - myColumn);
              component.fireConstraintsChanged(oldConstraints);
            }

            i = constraints.getRow() + constraints.getRowSpan() - 1;
          }
        }
      }
    }
  }
}