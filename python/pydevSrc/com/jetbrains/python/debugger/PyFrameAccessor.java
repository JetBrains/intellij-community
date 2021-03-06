package com.jetbrains.python.debugger;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.debugger.pydev.PyDebugCallback;
import com.jetbrains.python.debugger.pydev.dataviewer.DataViewerCommandBuilder;
import com.jetbrains.python.debugger.pydev.dataviewer.DataViewerCommandResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Facade to access python variables frame
 *
 * @author traff
 */
public interface PyFrameAccessor {
  PyDebugValue evaluate(final String expression, final boolean execute, boolean doTrunc) throws PyDebuggerException;

  /**
   * @param frame if null, then `XDebuggerSession#getCurrentStackFrame` is used
   */
  @Nullable
  XValueChildrenList loadFrame(@Nullable XStackFrame frame) throws PyDebuggerException;

  XValueChildrenList loadVariable(PyDebugValue var) throws PyDebuggerException;

  void changeVariable(PyDebugValue variable, String expression) throws PyDebuggerException;

  @Nullable
  PyReferrersLoader getReferrersLoader();

  /**
   * @throws IllegalArgumentException if the type of the {@code var} is not supported
   *                                  or the corresponding array has more than two dimensions
   */
  ArrayChunk getArrayItems(PyDebugValue var, int rowOffset, int colOffset, int rows, int cols, String format) throws PyDebuggerException;

  default DataViewerCommandResult executeDataViewerCommand(DataViewerCommandBuilder builder) throws PyDebuggerException {
    Logger.getInstance(this.getClass()).warn("executeDataViewerCommand is not supported on this PyFrameAccessor");
    return DataViewerCommandResult.NOT_IMPLEMENTED;
  }

  @Nullable
  XSourcePosition getSourcePositionForName(@Nullable String name, @Nullable String parentType);

  @Nullable
  XSourcePosition getSourcePositionForType(String type);

  default void showNumericContainer(@NotNull PyDebugValue value) {}

  default void addFrameListener(@NotNull PyFrameListener listener) {}

  default void loadAsyncVariablesValues(@Nullable XStackFrame frame, @NotNull final List<PyAsyncValue<String>> pyAsyncValues) {}

  default boolean isFrameCached(@NotNull XStackFrame frame) {
    return false;
  }

  default void setCurrentRootNode(@NotNull XCompositeNode node) {}

  default boolean isSimplifiedView() {
    return false;
  }

  @Nullable
  default XCompositeNode getCurrentRootNode() {
    return null;
  }

  class PyAsyncValue<T> {
    private final @NotNull PyDebugValue myDebugValue;
    private final @NotNull PyDebugCallback<T> myCallback;

    public PyAsyncValue(@NotNull PyDebugValue debugValue, @NotNull PyDebugCallback<T> callback) {
      myDebugValue = debugValue;
      myCallback = callback;
    }

    @NotNull
    public PyDebugValue getDebugValue() {
      return myDebugValue;
    }

    @NotNull
    public PyDebugCallback<T> getCallback() {
      return myCallback;
    }
  }
}
