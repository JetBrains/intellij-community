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
    if (isOneColumn() && isOneRow()) {
      onlyChunkSlice = "";
    }
    else if (isOneRow()) {
      onlyChunkSlice = "[" + cOffset + ":" + (cOffset + columns) + "]";
    }
    else if (isOneColumn()) {
      onlyChunkSlice = "[" + rOffset + ":" + (rOffset + rows) + "]";
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
          myValueProvider.showError(errorMessage, getInstance());
        }
      };

      XDebuggerTreeListener treeListener = new XDebuggerTreeListener() {
        @Override
        public void nodeLoaded(@NotNull RestorableStateNode node, String name) {
        }

        @Override
        public void childrenLoaded(@NotNull XDebuggerTreeNode node, @NotNull List<XValueContainerNode<?>> children, boolean last) {
          if (!(node instanceof XValueNodeImpl)) {
            return;
          }

          String fullName = ((XValueNodeImpl)node).getName();
          if (!fullName.contains(getPresentation())) {
            return;
          }

          int row = -1;
          if (isOneRow()) {
            row = 0;
          }
          else {
            if (isOneColumn()) {
              row = Integer.parseInt(fullName.substring(fullName.lastIndexOf('[') + 1, fullName.length() - 1));
            }
            else {
              fullName = fullName.substring(0, fullName.lastIndexOf(","));
              row = Integer.parseInt(fullName.substring(fullName.lastIndexOf('[') + 1, fullName.length()));
            }
          }

          if (row > rows) {
            throw new IllegalStateException("Row " + row + " is out of range for " + getPresentation() + ".");
          }

          if (row != -1 && myData[row][0] == null) {
            for (int i = 0; i < node.getChildCount() - 1; i++) {
              String rawValue = ((XValueNodeImpl)node.getChildAt(i + 1)).getRawValue();
              if (myValueProvider.isNumeric()) {
                //remove str quotes in case of numeric
                rawValue = rawValue.substring(1, rawValue.length() - 1);
              }
              myData[row][i] = rawValue;
            }
            if (node.getChildCount() == 0) {
              String rawValue = ((XValueNodeImpl)node).getRawValue();
              if (myValueProvider.isNumeric()) {
                //remove str quotes in case of numeric
                rawValue = rawValue.substring(1, rawValue.length() - 1);
              }
              myData[row][0] = rawValue;
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
      if (!isOneRow() && !isOneColumn()) {
        evalRowCommand += "[" + nextRow + ", 0:" + columns + "]))";
      }

      if (isOneRow() && isOneColumn()) {
        evalRowCommand = "\'" + myFormat + "\'" + " % " + getPresentation();
      }
      else if (isOneColumn()) {
        evalRowCommand = "\'" + myFormat + "\'" + " % " + getPresentation();
        evalRowCommand += "[" + nextRow + "]";
      }
      else if (isOneRow()) {
        evalRowCommand += "[0:" + columns + "]))";
      }
      myValueProvider.getEvaluator().evaluate(evalRowCommand, callback, null);
    }
  }

  private boolean isOneRow() {
    return rows == 1;
  }

  private boolean isOneColumn() {
    return columns == 1;
  }

  public Object[][] getData() {
    return myDataEvaluator.getData();
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


