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

import com.intellij.openapi.util.Pair;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener;
import com.intellij.xdebugger.impl.ui.tree.nodes.RestorableStateNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.jetbrains.python.debugger.PyDebugValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author amarch
 */
class Numpy2DArraySlice {
  private String myValueName;
  private List<Pair<Integer, Integer>> myFullSlice;
  private int[] myShape;
  private String myDtype;
  private String mySlicePresentation;
  private NumpyArrayValueProvider myValueProvider;
  private DataEvaluator myDataEvaluator;
  private int myRows = 0;
  private int myColumns = 0;
  private int myRowsOffset = 0;
  private int myColumnsOffset = 0;
  private String myFormat;

  public Numpy2DArraySlice(@NotNull String valueName,
                           @NotNull List<Pair<Integer, Integer>> fullSlice,
                           @NotNull NumpyArrayValueProvider valueProvider,
                           @NotNull int[] shape,
                           @Nullable String dtype,
                           @NotNull String format) {
    myValueName = valueName;
    myFullSlice = fullSlice;
    myValueProvider = valueProvider;
    myShape = shape;
    myDtype = dtype;
    myDataEvaluator = new DataEvaluator();
    myFormat = format;

    checkShapeConsistency();
  }

  public Numpy2DArraySlice getInstance() {
    return this;
  }

  private void checkShapeConsistency() throws IllegalStateException {
    boolean consistent = myFullSlice.size() == myShape.length || myFullSlice.size() == myShape.length - 1;
    if (consistent) {
      if (myFullSlice.size() == myShape.length - 1) {
        myFullSlice.add(new Pair<Integer, Integer>(0, myShape[myShape.length - 1]));
      }
      for (int index = 0; index < myFullSlice.size() - 2; index++) {
        Pair<Integer, Integer> slice = myFullSlice.get(index);
        consistent = slice.getFirst().equals(slice.getSecond());
        if (!consistent) break;
      }
    }

    if (!consistent) {
      throw new IllegalStateException("Illegal slice shape.");
    }

    int size = myFullSlice.size();
    myRows = myFullSlice.get(size - 2).getSecond() - myFullSlice.get(size - 2).getFirst();
    myColumns = myFullSlice.get(size - 1).getSecond() - myFullSlice.get(size - 1).getFirst();
    myRowsOffset = myFullSlice.get(size - 2).getFirst();
    myColumnsOffset = myFullSlice.get(size - 1).getFirst();
  }

  public String getPresentation() {
    if (mySlicePresentation == null) {
      fillPresentation();
    }
    return mySlicePresentation;
  }

  private void fillPresentation() {
    mySlicePresentation = myValueName;
    for (int index = 0; index < myFullSlice.size() - 2; index++) {
      mySlicePresentation += "[" + myFullSlice.get(index).getFirst() + "]";
    }
    mySlicePresentation +=
      "[" + myRowsOffset + ":" + (myRowsOffset + myRows) + ", " + myColumnsOffset + ":" + (myColumnsOffset + myColumns) + "]";
  }

  public void startFillData(Runnable callback) {
    myDataEvaluator.evaluateData(callback);
  }

  public boolean dataFilled() {
    return myDataEvaluator.dataFilled();
  }

  public void applyFormat(@NotNull String newFormat, Runnable callback) {
    if (newFormat.equals(myFormat)) {
      return;
    }
    myFormat = newFormat;
    myDataEvaluator.evaluateData(callback);
  }

  private class DataEvaluator {
    private Object[][] myData;
    private int myFilledRows = 0;
    private int nextRow = 0;

    public Object[][] getData() {
      return myData;
    }

    public boolean dataFilled() {
      return myRows > 0 && myFilledRows == myRows;
    }


    public void evaluateData(final Runnable callback) {
      final XDebuggerEvaluator.XEvaluationCallback computeChildrenCallback = new XDebuggerEvaluator.XEvaluationCallback() {
        @Override
        public void evaluated(@NotNull final XValue result) {
          final String name = ((PyDebugValue)result).getName();
          DebuggerUIUtil.invokeLater(new Runnable() {
            @Override
            public void run() {
              XValueNodeImpl node = new XValueNodeImpl(myValueProvider.getTree(), null, name, result);
              node.startComputingChildren();
            }
          });
        }

        @Override
        public void errorOccurred(@NotNull String errorMessage) {
          myValueProvider.showError(errorMessage);
        }
      };

      XDebuggerTreeListener treeListener = new XDebuggerTreeListener() {
        @Override
        public void nodeLoaded(@NotNull RestorableStateNode node, String name) {
        }

        @Override
        public void childrenLoaded(@NotNull XDebuggerTreeNode node, @NotNull List<XValueContainerNode<?>> children, boolean last) {
          String fullName = ((XValueNodeImpl)node).getName();
          if (fullName != null && fullName.contains("[")) {
            fullName = fullName.substring(0, fullName.lastIndexOf("["));
          }
          int row = -1;
          if (fullName != null && fullName.contains("[")) {
            row = Integer.parseInt(fullName.substring(fullName.lastIndexOf('[') + 1, fullName.length() - 1));
          }
          else if (fullName != null && !fullName.contains("[")) {
            row = 0;
          }
          if (row != -1 && myData[row][0] == null) {
            for (int i = 0; i < node.getChildCount() - 1; i++) {
              myData[row][i] = ((XValueNodeImpl)node.getChildAt(i + 1)).getRawValue();
            }
            myFilledRows += 1;
          }
          if (myFilledRows == myRows) {
            node.getTree().removeTreeListener(this);
            callback.run();
          }
          else {
            nextRow += 1;
            startEvalNextRow(computeChildrenCallback);
          }
        }
      };

      myData =
        new Object[myRows][myColumns];
      myRows = myData.length;
      myValueProvider.getTree().addTreeListener(treeListener);
      nextRow = myRowsOffset;
      myFilledRows = 0;
      startEvalNextRow(computeChildrenCallback);
    }

    private void startEvalNextRow(XDebuggerEvaluator.XEvaluationCallback callback) {
      String evalRowCommand = "map(lambda l: " + evalTypeFunc() + ", list(" + getUpperSlice(getPresentation());
      if (!isOneDimensional()) {
        evalRowCommand += "[" + nextRow + "]";
      }
      evalRowCommand +=
        "[" + myColumnsOffset + ":" + (myColumnsOffset + myColumns) + "]))";
      myValueProvider.getEvaluator().evaluate(evalRowCommand, callback, null);
    }

    private String getUpperSlice(String presentation) {
      return presentation.substring(0, presentation.lastIndexOf('['));
    }
  }

  private String evalTypeFunc() {
    String typeCommand = "(" + myFormat + " % l)";
    if (myDtype.equals("f")) {
      typeCommand = "float" + typeCommand;
    } else if (myDtype.equals("i") || myDtype.equals("u")) {
      typeCommand =  "int" + typeCommand;
    } else if (myDtype.equals("b")) {
      typeCommand =  "l";
    } else if (myDtype.equals("c")) {
      typeCommand =  "complex" + typeCommand;
    } else {
      typeCommand = "str" + typeCommand;
    }
    return typeCommand;
  }

  private boolean isOneDimensional() {
    return myShape.length == 2 && myShape[0] == 1;
  }


  public int[] getShape() {
    return myShape;
  }

  public void setShape(int[] shape) {
    myShape = shape;
  }

  public String getDtype() {
    return myDtype;
  }

  public void setDtype(String dtype) {
    myDtype = dtype;
  }

  public Object[][] getData() {
    return myDataEvaluator.getData();
  }
}

