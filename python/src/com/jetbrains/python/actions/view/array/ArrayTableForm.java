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

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author amarch
 */
public class ArrayTableForm {
  private EditorTextField mySliceTextField;
  private JCheckBox myColoredCheckbox;
  private JTextField myFormatTextField;
  private JBScrollPane myScrollPane;
  private JLabel myFormatLabel;
  private JPanel myFormatPanel;
  private JPanel myMainPanel;
  public JBTable myTable;
  private PyViewArrayAction.MyDialog myParentDialog;
  private Project myProject;
  private java.util.List<Pair<Integer, Integer>> myGuarded = new ArrayList<Pair<Integer, Integer>>();


  private static final String DATA_LOADING_IN_PROCESS = "Please wait, load array data.";

  private static final String NOT_APPLICABLE = "View not applicable for ";
  private static final Pattern EDITABLE_IN_SLICE_PATTERN = Pattern.compile("(\\[[0-9]*\\])|(\\[[0-9:]*)|([0-9:]*\\])");
  private NumpyArrayValueProvider myValueProvider;
  private Runnable myCallback;

  public ArrayTableForm(PyViewArrayAction.MyDialog dialog, Project project) {
    myParentDialog = dialog;
    myProject = project;
  }


  public class JBTableWithRows extends JBTable {
    private RowNumberTable myRowNumberTable;
    private EditorTextField mySliceField;

    public boolean getScrollableTracksViewportWidth() {
      return getPreferredSize().width < getParent().getWidth();
    }

    public RowNumberTable getRowNumberTable() {
      return myRowNumberTable;
    }

    public void setRowNumberTable(RowNumberTable rowNumberTable) {
      myRowNumberTable = rowNumberTable;
    }

    public EditorTextField getSliceField() {
      return mySliceField;
    }

    public void setSliceField(EditorTextField sliceField) {
      mySliceField = sliceField;
    }
  }

  private void createUIComponents() {
    myTable = new JBTableWithRows();

    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    myTable.setRowSelectionAllowed(false);
    myTable.getTableHeader().setReorderingAllowed(false);

    myScrollPane = new JBScrollPane();
    JTable rowTable = new RowNumberTable(myTable) {
      @Override
      protected void paintComponent(@NotNull Graphics g) {
        getEmptyText().setText("");
        super.paintComponent(g);
      }
    };
    myScrollPane.setRowHeaderView(rowTable);
    myScrollPane.setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER,
                           rowTable.getTableHeader());

    mySliceTextField = new EditorTextField("value[0][0:, 0:]", myProject, PythonFileType.INSTANCE);
    mySliceTextField.addNotify();


    mySliceTextField.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        TextAttributes defaultTestAttributes =
          EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.LIVE_TEMPLATE_ATTRIBUTES);

        Matcher m = EDITABLE_IN_SLICE_PATTERN.matcher(mySliceTextField.getText());
        int guard = 0;
        while (m.find()) {
          int start = m.start();
          int end = m.end();
          String match = m.group();

          if (match.contains("[")) {
            start += 1;
          }

          if (match.contains("]")) {
            end -= 1;
          }

          RangeHighlighter
            rh = mySliceTextField.getEditor().getMarkupModel().addRangeHighlighter(start, end, HighlighterLayer.LAST + 1,
                                                                                   new TextAttributes(
                                                                                     defaultTestAttributes.getForegroundColor(),
                                                                                     defaultTestAttributes.getBackgroundColor(),
                                                                                     JBColor.BLUE,
                                                                                     defaultTestAttributes.getEffectType(),
                                                                                     defaultTestAttributes.getFontType()),
                                                                                   HighlighterTargetArea.EXACT_RANGE);
          rh.setGreedyToLeft(true);
          rh.setGreedyToRight(true);
          mySliceTextField.getDocument().createGuardedBlock(guard, start);
          myGuarded.add(new Pair<Integer, Integer>(guard, start));
          guard = end;
        }
        mySliceTextField.getDocument().createGuardedBlock(guard, mySliceTextField.getText().length());
        myGuarded.add(new Pair<Integer, Integer>(guard, mySliceTextField.getText().length()));
      }

      @Override
      public void focusLost(FocusEvent e) {
        mySliceTextField.getEditor().getMarkupModel().removeAllHighlighters();
        for (Pair<Integer, Integer> p : myGuarded) {
          RangeMarker block = mySliceTextField.getDocument().getRangeGuard(p.getFirst(), p.getSecond());
          if (block != null) {
            mySliceTextField.getDocument().removeGuardedBlock(block);
          }
        }
      }
    });

    ((JBTableWithRows)myTable).setRowNumberTable((RowNumberTable)rowTable);
    ((JBTableWithRows)myTable).setSliceField(mySliceTextField);

    FixSizeTableAdjustmentListener tableAdjustmentListener =
      new FixSizeTableAdjustmentListener<NumpyArraySlice>(myTable, 50, 50, 40, 40, 2, 2) {
        @Override
        NumpyArraySlice createChunk(String baseSlice, int rows, int columns, int rOffset, int cOffset) {
          return new NumpyArraySlice(baseSlice, rows, columns, rOffset, cOffset, myValueProvider, myCallback);
        }

        @Override
        String getBaseSlice() {
          return NumpyArraySlice.getUpperSlice(mySliceTextField.getText(), 2);
        }
      };

    myScrollPane.getHorizontalScrollBar()
      .addAdjustmentListener(tableAdjustmentListener);

    myScrollPane.getVerticalScrollBar()
      .addAdjustmentListener(tableAdjustmentListener);
  }

  public EditorTextField getSliceTextField() {
    return mySliceTextField;
  }

  public JTextField getFormatTextField() {
    return myFormatTextField;
  }

  public JBTable getTable() {
    return myTable;
  }

  public JCheckBox getColored() {
    return myColoredCheckbox;
  }

  public void setDefaultStatus() {
    if (myTable != null) {
      myTable.getEmptyText().setText(DATA_LOADING_IN_PROCESS);
      myTable.setPaintBusy(true);
    }
  }

  public void setErrorText(Exception e) {
    setErrorText(e.getMessage());
  }

  public void setErrorText(String message) {
    myParentDialog.setError(message);
  }

  public void setNotApplicableStatus(XValueNodeImpl node) {
    myTable.getEmptyText().setText(NOT_APPLICABLE + node.getName());
  }

  public JComponent getMainPanel() {
    return myMainPanel;
  }

  public void setArrayValueProvider(NumpyArrayValueProvider provider) {
    myValueProvider = provider;
  }

  public void setCallback(Runnable callback) {
    myCallback = callback;
  }
}
