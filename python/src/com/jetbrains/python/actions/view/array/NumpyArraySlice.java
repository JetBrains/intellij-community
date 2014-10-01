package com.jetbrains.python.actions.view.array;

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

import java.util.List;

/**
 * @author amarch
 */

class NumpyArraySlice extends ArrayChunk {
  private NumpyArrayValueProvider myValueProvider;
  private DataEvaluator myDataEvaluator;

  public NumpyArraySlice(String baseSlice,
                         int rows,
                         int columns,
                         int rOffset,
                         int cOffset,
                         @NotNull NumpyArrayValueProvider valueProvider,
                         @NotNull Runnable callback) {
    super(baseSlice, rows, columns, rOffset, cOffset);
    myValueProvider = valueProvider;
    myDataEvaluator = new DataEvaluator();
  }

  public String getChunkPresentation() {
    return baseSlice + "[" + rOffset + ":" + (rOffset + rows) + ", " + cOffset + ":" + (cOffset + columns) + "]";
  }

  @Override
  void fillData(Runnable callback) {
    startFillData(callback);
  }

  public NumpyArraySlice getInstance() {
    return this;
  }

  public void startFillData(Runnable callback) {
    myDataEvaluator.evaluateData(callback);
  }

  public boolean dataFilled() {
    return myDataEvaluator.dataFilled();
  }

  private class DataEvaluator {
    private Object[][] myData;
    private int myFilledRows = 0;
    private int nextRow = 0;

    public Object[][] getData() {
      return myData;
    }

    public boolean dataFilled() {
      return rows > 0 && myFilledRows == rows;
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
            fullName = fullName.substring(0, fullName.lastIndexOf(","));
          }
          int row = -1;
          if (fullName != null && fullName.contains("[")) {
            row = Integer.parseInt(fullName.substring(fullName.lastIndexOf('[') + 1, fullName.length()));
          }
          else if (fullName != null && !fullName.contains("[")) {
            row = 0;
          }
          if (row != -1 && myData[row][0] == null) {
            for (int i = 0; i < node.getChildCount() - 1; i++) {
              myData[row][i] = ((XValueNodeImpl)node.getChildAt(i + 1)).getRawValue();
            }
            System.out.println("Add filled row " + myFilledRows);
            myFilledRows += 1;
          }
          if (myFilledRows == rows) {
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
        new Object[rows][columns];
      myValueProvider.getTree().addTreeListener(treeListener);
      nextRow = 0;
      myFilledRows = 0;
      startEvalNextRow(computeChildrenCallback);
    }

    private void startEvalNextRow(XDebuggerEvaluator.XEvaluationCallback callback) {
      //String evalRowCommand = "map(lambda l: " + evalTypeFunc() + ", list(" + getUpperSlice(getPresentation());
      //String evalRowCommand = "list(" + getUpperSlice(getChunkPresentation());
      //if (!isOneDimensional()) {
      //  evalRowCommand += "[" + nextRow + "]";
      //}
      //evalRowCommand +=
      //  "[" + (40 - getRows() + nextRow) + ", " + cOffset + ":" + (cOffset + columns) + "])";

      String evalRowCommand = "list(" + getChunkPresentation() + "[" + nextRow + ", 0:" + columns + "])";

      System.out.println(rOffset + " " + getRows() + " " + nextRow);
      System.out.println(evalRowCommand);
      myValueProvider.getEvaluator().evaluate(evalRowCommand, callback, null);
    }
  }

  public static String getUpperSlice(String presentation, int up) {
    if (up < 1) {
      return "";
    }
    String upperSlice = presentation;
    for (int i = 0; i < up; i++) {
      if (upperSlice.contains("[")) {
        upperSlice = upperSlice.substring(0, upperSlice.lastIndexOf('['));
      } else {
        return upperSlice;
      }
    }
    return upperSlice;
  }

  private boolean isOneDimensional() {
    return !baseSlice.contains(",");
  }


  @Override
  int getRows() {
    return rows;
  }

  @Override
  int getColumns() {
    return columns;
  }

  public Object[][] getData() {
    return myDataEvaluator.getData();
  }
}


