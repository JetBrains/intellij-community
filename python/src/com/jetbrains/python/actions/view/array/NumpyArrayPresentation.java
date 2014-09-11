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

import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener;
import com.intellij.xdebugger.impl.ui.tree.nodes.RestorableStateNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.jetbrains.python.debugger.PyDebugValue;
import org.jetbrains.annotations.NotNull;

import javax.naming.directory.InvalidAttributeValueException;
import java.util.List;

/**
 * @author amarch
 */
class NumpyArrayPresentation {
  private Object[][] myData;
  private String mySlice;
  private String myArrayName;
  private int[] myShape;
  private int myRows = 0;
  private int myFilledRows = 0;
  private int nextRow = 0;
  private String myDtype;
  private NumpyArrayValueProvider myValueProvider;

  public NumpyArrayPresentation(String name, NumpyArrayValueProvider valueProvider) {
    myArrayName = name;
    myValueProvider = valueProvider;
  }

  public NumpyArrayPresentation getInstance() {
    return this;
  }

  public String getName() {
    return myArrayName;
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
    return myData;
  }

  public void fillShape(final boolean stop) {
    XDebuggerEvaluator.XEvaluationCallback callback = new XDebuggerEvaluator.XEvaluationCallback() {
      @Override
      public void evaluated(@NotNull XValue result) {
        try {
          myShape = parseShape(((PyDebugValue)result).getValue());

          if (myShape.length == 1) {
            myShape = new int[]{1, myShape[0]};
          }

          myData = new Object[myShape[myShape.length - 2]][myShape[myShape.length - 1]];
          myRows = myShape[myShape.length - 2];
          if (!stop) {
            myValueProvider.startFillTable(getInstance());
          }
        }
        catch (InvalidAttributeValueException e) {
          errorOccurred(e.getMessage());
        }
      }

      @Override
      public void errorOccurred(@NotNull String errorMessage) {
        myValueProvider.getComponent().setErrorSpinnerText(errorMessage);
      }
    };
    fillShape(callback);
  }

  public void fillShape(XDebuggerEvaluator.XEvaluationCallback callback) {
    String evalShapeCommand = myArrayName + ".shape";
    myValueProvider.getEvaluator().evaluate(evalShapeCommand, callback, null);
  }

  public void fillSliceShape(final XDebuggerEvaluator.XEvaluationCallback callback) {
    if (mySlice == null) {
      callback.errorOccurred("Null slice");
      return;
    }
    XDebuggerEvaluator.XEvaluationCallback innerCallback = new XDebuggerEvaluator.XEvaluationCallback() {
      @Override
      public void evaluated(@NotNull XValue result) {
        try {
          myShape = parseShape(((PyDebugValue)result).getValue());

          if (myShape.length > 2) {
            errorOccurred("Slice not present valid 2d array.");
            return;
          }

          if (myShape.length == 1) {
            myShape = new int[]{1, myShape[0]};
          }
          myData = new Object[myShape[myShape.length - 2]][myShape[myShape.length - 1]];
          myRows = myShape[myShape.length - 2];
          callback.evaluated(result);
        }
        catch (InvalidAttributeValueException e) {
          errorOccurred(e.getMessage());
        }
      }

      @Override
      public void errorOccurred(@NotNull String errorMessage) {
        callback.errorOccurred(errorMessage);
      }
    };

    String evalShapeCommand = mySlice + ".shape";
    myValueProvider.getEvaluator().evaluate(evalShapeCommand, innerCallback, null);
  }

  private int[] parseShape(String shape) throws InvalidAttributeValueException {
    String[] dimensions = shape.substring(1, shape.length() - 1).trim().split(",");
    if (dimensions.length > 0) {
      int[] result = new int[dimensions.length];
      for (int i = 0; i < dimensions.length; i++) {
        result[i] = Integer.parseInt(dimensions[i].trim());
      }
      return result;
    }
    else {
      throw new InvalidAttributeValueException("Invalid shape string for " + myValueProvider.getNodeName());
    }
  }

  public void fillType(final boolean stop) {
    XDebuggerEvaluator.XEvaluationCallback callback = new XDebuggerEvaluator.XEvaluationCallback() {
      @Override
      public void evaluated(@NotNull XValue result) {
        myDtype = ((PyDebugValue)result).getValue();
        if (!stop) {
          myValueProvider.startFillTable(getInstance());
        }
      }

      @Override
      public void errorOccurred(@NotNull String errorMessage) {

      }
    };
    String evalTypeCommand = myArrayName + ".dtype.kind";
    myValueProvider.getEvaluator().evaluate(evalTypeCommand, callback, null);
  }

  public boolean dataFilled() {
    return myRows > 0 && myFilledRows == myRows;
  }

  public void fillData(final boolean stop) {
    //todo: need full value evaluator
    final XDebuggerEvaluator.XEvaluationCallback callback = new XDebuggerEvaluator.XEvaluationCallback() {
      @Override
      public void evaluated(@NotNull XValue result) {
        String name = ((PyDebugValue)result).getName();
        XValueNodeImpl node = new XValueNodeImpl(myValueProvider.getTree(), null, name, result);
        node.startComputingChildren();
      }

      @Override
      public void errorOccurred(@NotNull String errorMessage) {
      }
    };

    XDebuggerTreeListener treeListener = new XDebuggerTreeListener() {
      @Override
      public void nodeLoaded(@NotNull RestorableStateNode node, String name) {
      }

      @Override
      public void childrenLoaded(@NotNull XDebuggerTreeNode node, @NotNull List<XValueContainerNode<?>> children, boolean last) {
        String fullName = ((XValueNodeImpl)node).getName();
        int row = 0;
        if (fullName != null && fullName.contains("[")) {
          row = Integer.parseInt(fullName.substring(fullName.lastIndexOf('[') + 1, fullName.length() - 2));
        }
        if (myData[row][0] == null) {
          for (int i = 0; i < node.getChildCount() - 1; i++) {
            myData[row][i] = ((XValueNodeImpl)node.getChildAt(i + 1)).getRawValue();
          }
          myFilledRows += 1;
        }
        if (myFilledRows == myRows) {
          node.getTree().removeTreeListener(this);
          if (!stop) {
            myValueProvider.startFillTable(getInstance());
          }
        }
        else {
          nextRow += 1;
          startEvalNextRow(callback);
        }
      }
    };

    myValueProvider.getTree().addTreeListener(treeListener);
    nextRow = 0;
    startEvalNextRow(callback);
  }

  private void startEvalNextRow(XDebuggerEvaluator.XEvaluationCallback callback) {
    String evalRowCommand = "list(" + myArrayName;
    if (myShape.length > 2) {
      evalRowCommand += new String(new char[myShape.length - 2]).replace("\0", "[0]");
    }

    if (myShape[0] > 1) {
      evalRowCommand += "[" + nextRow + "])";
    }
    else {
      evalRowCommand += ")";
    }
    myValueProvider.getEvaluator().evaluate(evalRowCommand, callback, null);
  }

  public String getSlice() {
    return mySlice;
  }

  public void setSlice(String slice) {
    mySlice = slice;
  }

  public void computeSlice() {
    String presentation = "";

    presentation += myValueProvider.getNodeName();

    if (myShape != null) {
      presentation += new String(new char[myShape.length - 2]).replace("\0", "[0]");
      if (myShape[0] == 1) {
        presentation += "[0:" + myShape[1] + "]";
      }
      else {
        presentation += "[0:" + myShape[myShape.length - 2] + "]";
      }
    }

    setSlice(presentation);
  }
}

