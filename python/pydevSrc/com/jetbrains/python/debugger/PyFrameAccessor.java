package com.jetbrains.python.debugger;

import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.debugger.pydev.PyDebugCallback;
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

  @Nullable
  XValueChildrenList loadFrame() throws PyDebuggerException;

  XValueChildrenList loadVariable(PyDebugValue var) throws PyDebuggerException;

  void changeVariable(PyDebugValue variable, String expression) throws PyDebuggerException;

  @Nullable
  PyReferrersLoader getReferrersLoader();

  ArrayChunk getArrayItems(PyDebugValue var, int rowOffset, int colOffset, int rows, int cols, String format) throws PyDebuggerException;

  @Nullable
  XSourcePosition getSourcePositionForName(@Nullable String name, @Nullable String parentType);

  @Nullable
  XSourcePosition getSourcePositionForType(String type);

  default void showNumericContainer(@NotNull PyDebugValue value) {}

  default void addFrameListener(@NotNull PyFrameListener listener) {}

  default void loadAsyncVariablesValues(@NotNull final List<PyAsyncValue<String>> pyAsyncValues) {}

  default boolean isCurrentFrameCached() {
    return false;
  }

  default void setCurrentRootNode(@NotNull XCompositeNode node) {}

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
