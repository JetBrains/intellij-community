/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.debugger.array;

import com.intellij.openapi.project.Project;
import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.containerview.ColoredCellRenderer;
import com.jetbrains.python.debugger.containerview.NumericContainerViewTable;
import com.jetbrains.python.debugger.containerview.ViewNumericContainerDialog;
import org.jetbrains.annotations.NotNull;
/**
 * @author amarch
 */
public final class NumpyArrayTable extends NumericContainerViewTable {


  private ArrayTableCellRenderer myArrayTableCellRenderer;

  public NumpyArrayTable(@NotNull Project project,
                         @NotNull ViewNumericContainerDialog dialog, @NotNull PyDebugValue value) {
    super(project, dialog, value);
  }

  @Override
  protected AsyncArrayTableModel createTableModel(int rowCount, int columnCount) {
    return new AsyncArrayTableModel(rowCount, columnCount, this);
  }

  @Override
  protected ColoredCellRenderer createCellRenderer(double minValue, double maxValue, ArrayChunk chunk) {
    myArrayTableCellRenderer = new ArrayTableCellRenderer(minValue, maxValue, chunk.getType());
    fillColorRange(chunk.getMin(), chunk.getMax());
    return myArrayTableCellRenderer;
  }

  @Override
  protected final String getTitlePresentation(String slice) {
    return "Array View: " + slice;
  }


  private void fillColorRange(String minValue, String maxValue) {
    double min;
    double max;
    if ("c".equals(myDtypeKind)) {
      min = 0;
      max = 1;
      myArrayTableCellRenderer.setComplexMin(minValue);
      myArrayTableCellRenderer.setComplexMax(maxValue);
    }
    else if ("b".equals(myDtypeKind)) {
      min = minValue.equals("True") ? 1 : 0;
      max = maxValue.equals("True") ? 1 : 0;
    }
    else {
      min = Double.parseDouble(minValue);
      max = Double.parseDouble(maxValue);
    }

    myArrayTableCellRenderer.setMin(min);
    myArrayTableCellRenderer.setMax(max);
  }

  @Override
  public boolean isNumeric() {
    if (myDtypeKind != null) {
      return "biufc".contains(myDtypeKind.substring(0, 1));
    }
    return false;
  }
}
