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

import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashSet;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener;
import com.intellij.xdebugger.impl.ui.tree.nodes.RestorableStateNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.List;

/**
* @author amarch
*/
public class XDebuggerTreeTableListener implements XDebuggerTreeListener {

  XValueNodeImpl baseNode;

  XValueContainerNode innerNdarray;

  XValueContainerNode innerItems;

  ArrayTableComponent myComponent;

  Project myProject;

  JTable myTable;

  int[] shape;

  int depth = 0;

  int loadedRows = 0;

  HashSet<String> unloadedRowNumbers = new HashSet<String>();

  boolean baseChildrenLoaded = false;

  boolean numeric = false;

  boolean dataLoaded = false;

  Object[][] data;

  public XDebuggerTreeTableListener(XValueNodeImpl node, JTable table, ArrayTableComponent component, Project project) {
    super();
    baseNode = baseNode;
    myTable = table;
    myComponent = component;
    myProject= project;
  }

  @Override
  public void nodeLoaded(@NotNull RestorableStateNode node, String name) {
    System.out.printf(name + " node loaded\n");

    if (!baseChildrenLoaded &&
        (shape == null || name.equals("dtype")) &&
        ((XValueNodeImpl)node.getParent()).getName().equals(baseNode.getName())) {
      if (name.equals("shape")) {
        String rawValue = node.getRawValue();
        String[] shapes = rawValue.substring(1, rawValue.length() - 1).split(",");
        shape = new int[shapes.length];
        for (int i = 0; i < shapes.length; i++) {
          shape[i] = Integer.parseInt(shapes[i].trim());
        }
        depth = Math.max(shape.length - 2, 0);
      }

      if (name.equals("dtype")) {
        String rawValue = node.getRawValue();
        if ("biufc".contains(rawValue.substring(0, 1))) {
          numeric = true;
        }
      }
    }
  }

  @Override
  public void childrenLoaded(@NotNull XDebuggerTreeNode node, @NotNull List<XValueContainerNode<?>> children, boolean last) {
    System.out.printf(children + "children loaded\n");

    if (dataLoaded) {
      return;
    }

    //todo: not compute children if they yet computed

    if (!baseChildrenLoaded && node.equals(baseNode)) {
      baseChildrenLoaded = true;
      innerNdarray = (XValueContainerNode)node;
      if (shape != null) {
        if (shape.length >= 2) {
          data = new Object[shape[shape.length - 2]][shape[shape.length - 1]];
        }
        else {
          data = new Object[1][shape[0]];
        }
      }
    }

    //go deeper
    if (depth > 0) {
      if (innerNdarray != null && innerNdarray.equals(node)) {
        innerNdarray = null;
        innerItems = findItems(node);
        innerItems.startComputingChildren();
      }

      if (innerItems != null && innerItems.equals(node)) {
        innerNdarray = (XValueContainerNode)node.getChildAt(1);
        innerItems = null;
        innerNdarray.startComputingChildren();
        depth -= 1;
      }

      return;
    }

    //find ndarray slice to display
    if (depth == 0) {
      innerItems = findItems(node);
      innerItems.startComputingChildren();
      depth -= 1;
      return;
    }

    if (depth == -1 && node.equals(innerItems)) {
      if (shape != null && shape.length == 1) {
        for (int i = 0; i < node.getChildCount() - 1; i++) {
          data[0][i] = ((XValueNodeImpl)node.getChildAt(i + 1)).getRawValue();
        }
        loadData();
        loadedRows = 1;
      }
      else {
        for (int i = 0; i < node.getChildCount() - 1; i++) {
          ((XValueNodeImpl)node.getChildAt(i + 1)).startComputingChildren();
          unloadedRowNumbers.add(((XValueNodeImpl)node.getChildAt(i + 1)).getName());
        }
        depth -= 1;
      }
      return;
    }


    if (depth == -2) {
      String name = ((XValueNodeImpl)node).getName();
      // ndarrray children not computed yet
      if (unloadedRowNumbers.contains(name)) {
        unloadedRowNumbers.remove(name);
        findItems(node).startComputingChildren();
        return;
      }

      if (name.startsWith("[")) {
        int row = Integer.parseInt((((XValueNodeImpl)node.getParent()).getName()));
        if (data[row][0] == null) {
          for (int i = 0; i < node.getChildCount() - 1; i++) {
            data[row][i] = ((XValueNodeImpl)node.getChildAt(i + 1)).getRawValue();
          }
          loadedRows += 1;
        }
      }
    }

    if (loadedRows == shape[shape.length - 2]) {
      loadData();
    }
  }

  XValueContainerNode findItems(@NotNull XDebuggerTreeNode node) {
    for (int i = 0; i < node.getChildCount(); i++) {
      if (node.getChildAt(i).toString().startsWith("[")) {
        return (XValueContainerNode)node.getChildAt(i);
      }
    }
    return null;
  }

  private String[] range(int min, int max) {
    String[] array = new String[max - min + 1];
    for (int i = min; i <= max; i++) {
      array[i] = Integer.toString(i);
    }
    return array;
  }

  private void loadData() {

    DefaultTableModel model = new DefaultTableModel(data, range(0, data[0].length - 1));

    myTable.setModel(model);
    myTable.setDefaultEditor(myTable.getColumnClass(0), new ArrayTableCellEditor(myProject));

    if (numeric) {
      double min = Double.MAX_VALUE;
      double max = Double.MIN_VALUE;
      if (data.length > 0) {
        try {
          for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[0].length; j++) {
              double d = Double.parseDouble(data[i][j].toString());
              min = min > d ? d : min;
              max = max < d ? d : max;
            }
          }
        }
        catch (NumberFormatException e) {
          min = 0;
          max = 0;
        }
      }
      else {
        min = 0;
        max = 0;
      }

      myTable.setDefaultRenderer(myTable.getColumnClass(0), new ArrayTableCellRenderer(min, max));
    }
    else {
      myComponent.getColored().setSelected(false);
      myComponent.getColored().setVisible(false);
    }

    myComponent.getTextField().setText(getDefaultSliceRepresentation());
    dataLoaded = true;
    innerItems = null;
    innerNdarray = null;
  }

  public String getDefaultSliceRepresentation() {
    String representation = "";

    if (baseNode != null) {
      representation += baseNode.getName();
      if (shape != null && shape.length > 0) {
        for (int i = 0; i < shape.length - 2; i++) {
          representation += "[0]";
        }
        if (shape.length == 1) {
          representation += "[0:" + shape[0] + "]";
        }
        else {
          representation += "[0:" + shape[shape.length - 2] + "][0:" + shape[shape.length - 1] + "]";
        }
      }
    }

    return representation;
  }

}
