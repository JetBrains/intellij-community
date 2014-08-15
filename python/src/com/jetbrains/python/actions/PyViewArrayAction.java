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
package com.jetbrains.python.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.xdebugger.impl.ui.tree.SetValueInplaceEditor;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeInplaceEditor;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * @author amarch
 */

public class PyViewArrayAction extends XDebuggerTreeActionBase {

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    final MyDialog dialog = new MyDialog(e.getProject(), node, nodeName);
    dialog.setTitle("View Array");
    dialog.setValue(node);
    dialog.show();
  }

  private abstract class ArrayValueProvider {

    public abstract Object[][] parseValues(String rawValue);
  }

  private class NumpyArrayValueProvider extends ArrayValueProvider {

    @Override
    public Object[][] parseValues(String rawValue) {
      return new String[][]{{"a", "b", "c"}, {"a", "b", "c"}};
    }
  }

  private class MyDialog extends DialogWrapper {
    private JTable myTable;
    private XDebuggerTreeInplaceEditor myEditor;

    private MyDialog(Project project, XValueNodeImpl node, @NotNull String nodeName) {
      super(project, false);
      setModal(false);
      setCancelButtonText("Close");
      setCrossClosesWindow(true);

      myEditor = new SetValueInplaceEditor(node, nodeName);

      myTable = new JTable() {
        @Override
        public boolean isCellEditable(int row, int column) {
          return true;
        }
      };

      init();
    }

    public void setValue(XValueNodeImpl node) {
      ArrayValueProvider valueProvider;

      if (node.getValuePresentation().getType().equals("ndarray")) {
        valueProvider = new NumpyArrayValueProvider();
        final Object[][] data = valueProvider.parseValues(node.getRawValue());
        DefaultTableModel model = new DefaultTableModel(data, range(0, data.length));
        myTable.setModel(model);
      }
    }

    private String[] range(int min, int max) {
      String[] array = new String[max - min + 1];
      for (int i = min; i <= max; i++) {
        array[i] = new Integer(i).toString();
      }
      return array;
    }

    @Override
    @NotNull
    protected Action[] createActions() {
      return new Action[]{getCancelAction()};
    }

    @Override
    protected String getDimensionServiceKey() {
      return "#com.jetbrains.python.actions.PyViewArrayAction";
    }

    @Override
    protected JComponent createCenterPanel() {
      final JPanel panel = new JPanel(new BorderLayout());
      panel.add(myTable, BorderLayout.CENTER);
      //panel.add(myEditor.getEditorComponent());
      panel.setPreferredSize(new Dimension(450, 300));
      return panel;
    }
  }
}
