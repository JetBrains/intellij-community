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

class NumpyArraySlice extends ComparableArrayChunk {
  private NumpyArrayValueProvider myValueProvider;
  private DataEvaluator myDataEvaluator;
  private String myFormat;

  public NumpyArraySlice(String baseSlice,
                         int rows,
                         int columns,
                         int rOffset,
                         int cOffset,
                         String format,
                         @NotNull NumpyArrayValueProvider valueProvider) {
    super(baseSlice, rows, columns, rOffset, cOffset);
    myValueProvider = valueProvider;
    myDataEvaluator = new DataEvaluator();
    myFormat = format;
  }

  public String getPresentation() {
    String onlyChunkSlice = "[" + rOffset + ":" + (rOffset + rows) + ", " + cOffset + ":" + (cOffset + columns) + "]";
    if (isOneRow()) {
      onlyChunkSlice = "[" + cOffset + ":" + (cOffset + columns) + "]";
    }
    if (baseSlice.endsWith(onlyChunkSlice)) {
      return baseSlice;
    }
    return baseSlice + onlyChunkSlice;
  }

  @Override
  void fillData(Runnable callback) {
    myDataEvaluator.evaluateData(callback);
  }

  public NumpyArraySlice getInstance() {
    return this;
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
          if (!errorMessage.contains("Timeout waiting for response on")) {
            myValueProvider.showError(errorMessage);
          }
        }
      };

      XDebuggerTreeListener treeListener = new XDebuggerTreeListener() {
        @Override
        public void nodeLoaded(@NotNull RestorableStateNode node, String name) {
        }

        @Override
        public void childrenLoaded(@NotNull XDebuggerTreeNode node, @NotNull List<XValueContainerNode<?>> children, boolean last) {
          String fullName = ((XValueNodeImpl)node).getName();
          if (!fullName.contains(getPresentation())) {
            return;
          }

          int row = -1;
          if (isOneRow()) {
            row = 0;
          }
          else {
            if (fullName != null && fullName.contains("[")) {
              fullName = fullName.substring(0, fullName.lastIndexOf(","));
            }
            if (fullName != null && fullName.contains("[")) {
              row = Integer.parseInt(fullName.substring(fullName.lastIndexOf('[') + 1, fullName.length()));
            }
            else if (fullName != null && !fullName.contains("[")) {
              row = 0;
            }
          }

          if (row > rows) {
            throw new IllegalStateException("Row " + row + " is out of range for " + getPresentation() + ".");
          }

          if (row != -1 && myData[row][0] == null) {
            for (int i = 0; i < node.getChildCount() - 1; i++) {
              myData[row][i] = ((XValueNodeImpl)node.getChildAt(i + 1)).getRawValue();
            }
            myFilledRows += 1;
            if (myFilledRows == rows) {
              node.getTree().removeTreeListener(this);
              callback.run();
            }
            else {
              nextRow += 1;
              startEvalNextRow(computeChildrenCallback);
            }
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
      if (nextRow >= rows) {
        throw new IllegalStateException("Row " + nextRow + " is out of range for " + getPresentation() + ".");
      }

      String evalRowCommand = "map(lambda l: " + myValueProvider.evalTypeFunc(myFormat) + ", list(" + getPresentation();
      if (!isOneRow()) {
        evalRowCommand += "[" + nextRow + ", 0:" + columns + "]";
      }
      evalRowCommand += "))";
      myValueProvider.getEvaluator().evaluate(evalRowCommand, callback, null);
    }
  }

  private boolean isOneRow() {
    return rows == 1;
  }

  public Object[][] getData() {
    return myDataEvaluator.getData();
  }

  public void applyFormat(@NotNull String newFormat, Runnable callback) {
    if (newFormat.equals(myFormat)) {
      return;
    }
    myFormat = newFormat;
    myDataEvaluator.evaluateData(callback);
  }

  public String getFormat() {
    return myFormat;
  }

  public void setFormat(String format) {
    myFormat = format;
  }

  @Override
  public String toString() {
    return super.toString() + " : " + getPresentation();
  }
}


