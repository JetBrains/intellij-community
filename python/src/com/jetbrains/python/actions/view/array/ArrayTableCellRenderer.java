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
package com.jetbrains.python.actions.view.array;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
* @author amarch
*/
class ArrayTableCellRenderer extends DefaultTableCellRenderer {

  double min;
  double max;
  Color minColor;
  Color maxColor;
  boolean colored = true;

  public ArrayTableCellRenderer(double min, double max) {
    this.min = min;
    this.max = max;
    minColor = new Color(100, 0, 0, 200);
    maxColor = new Color(254, 0, 0, 200);
  }

  public void setColored(boolean colored) {
    this.colored = colored;
  }

  public Component getTableCellRendererComponent(JTable table, Object value,
                                                 boolean isSelected, boolean hasFocus, int rowIndex, int vColIndex) {
    if (isSelected) {
      // cell (and perhaps other cells) are selected
    }

    if (hasFocus) {
      // this cell is the anchor and the table has the focus
    }

    if (value != null) {
      setText(value.toString());
    }


    if (max != min) {
      if (colored) {
        try {
          double med = Double.parseDouble(value.toString());
          int r = (int)(minColor.getRed() + Math.round((maxColor.getRed() - minColor.getRed()) / (max - min) * (med - min)));
          this.setBackground(new Color(r % 256, 0, 0, 200));
        }
        catch (NumberFormatException e) {
        }
      }
      else {
        this.setBackground(new Color(255, 255, 255));
      }
    }


    return this;
  }
}
