// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.values;

import com.intellij.util.SmartFMap;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyFrameAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public final class DataFrameDebugValue extends PyDebugValue {
  private final ColumnNode treeColumns = new ColumnNode();

  private static final String PANDAS_COLUMN_NAMES_CODE = """
    try:
     import json
     if str(%1$s.columns.__class__) == "<class 'pandas.core.indexes.multi.MultiIndex'>":
       print(json.dumps({"columns": list(%1$s.columns), "isMultiIndex": True}))
     else:
       print(json.dumps({"columns": [[_] for _ in list(%1$s.columns)], "isMultiIndex": False}))
    except:
      pass
    """;

  private static final String PANDAS_COLUMN_NAMES_CODE_ONE_LINE =
    "__import__('json').dumps({\"columns\": list(%1$s.columns), \"isMultiIndex\": True}) " +
    "if str(%1$s.columns.__class__) == \"<class 'pandas.core.indexes.multi.MultiIndex'>\" " +
    "else __import__('json').dumps({\"columns\": [[_] for _ in list(%1$s.columns)], \"isMultiIndex\": False})";

  public DataFrameDebugValue(@NotNull String name,
                             @Nullable String type,
                             @Nullable String typeQualifier,
                             @Nullable String value,
                             boolean container,
                             @Nullable String shape,
                             boolean isReturnedVal,
                             boolean isIPythonHidden,
                             boolean errorOnEval,
                             @Nullable String typeRendererId,
                             @NotNull PyFrameAccessor frameAccessor) {
    super(name, type, typeQualifier, value, container, shape, isReturnedVal, isIPythonHidden, errorOnEval, typeRendererId, frameAccessor);
  }

  public DataFrameDebugValue(@NotNull String name,
                             @Nullable String type,
                             @Nullable String typeQualifier,
                             @Nullable String value,
                             boolean container,
                             @Nullable String shape,
                             boolean isReturnedVal,
                             boolean isIPythonHidden,
                             boolean errorOnEval,
                             @Nullable String typeRendererId,
                             @Nullable PyDebugValue parent,
                             @NotNull PyFrameAccessor frameAccessor) {
    super(name, type, typeQualifier, value, container, shape, isReturnedVal, isIPythonHidden, errorOnEval, typeRendererId, parent,
          frameAccessor);
  }

  public DataFrameDebugValue(@NotNull PyDebugValue value, @NotNull String newName) {
    super(value, newName);
  }

  public DataFrameDebugValue(@NotNull PyDebugValue value) {
    super(value);
  }

  public ColumnNode getTreeColumns() {
    return treeColumns;
  }


  public void setColumns(@NotNull InformationColumns informationColumns) {
    for (List<String> columns : informationColumns.columns) {
      ColumnNode node = treeColumns.addChildIfNotExist(columns.get(0));
      if (informationColumns.isMultiIndex) {
        for (int j = 1; j < columns.size(); j++) {
          node = node.addChildIfNotExist(columns.get(j));
        }
      }
    }
  }

  private static final String PYDEV_COMMAND_PREFIX = "# pydev_util_command\n";

  /**
   * @param dfName  - DataFrame identifier (it must be a valid Python identifier, either will be a useless function call)
   * @param oneLine - flag to construct one/multi line Python script
   */
  public static String commandExtractPandasColumns(@NotNull String dfName, boolean oneLine) {
    if (oneLine) {
      return String.format(PANDAS_COLUMN_NAMES_CODE_ONE_LINE, dfName);
    }
    else {
      return PYDEV_COMMAND_PREFIX +
             String.format(PANDAS_COLUMN_NAMES_CODE, dfName);
    }
  }

  public static final class InformationColumns {
    public boolean isMultiIndex;
    public List<List<String>> columns;
  }

  public static class ColumnNode {
    SmartFMap<String, ColumnNode> children;

    private ColumnNode() {
      this.children = SmartFMap.emptyMap();
    }

    public ColumnNode addChildIfNotExist(@NotNull String name) {
      if (children.containsKey(name)) {
        return children.get(name);
      }
      else {
        ColumnNode newNode = new ColumnNode();
        children = children.plus(name, newNode);
        return newNode;
      }
    }

    public ColumnNode getChildIfExist(@NotNull String name) {
      return children.get(name);
    }

    public Set<String> getChildrenName() {
      return children.keySet();
    }
  }
}



