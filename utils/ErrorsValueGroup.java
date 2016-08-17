package org.jetbrains.debugger.memory.utils;

import com.intellij.icons.AllIcons;
import com.intellij.util.containers.HashMap;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XNamedValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ErrorsValueGroup extends XValueGroup {
  private final Map<String, List<XNamedValue>> myErrorMessage2ValueMap = new HashMap<>();

  public ErrorsValueGroup(@NotNull String name) {
    super(name);
  }

  public void addErrorValue(@NotNull String message, @NotNull XNamedValue value) {
    List<XNamedValue> lst;
    if (!myErrorMessage2ValueMap.containsKey(message)) {
      myErrorMessage2ValueMap.put(message, new ArrayList<>());
    }

    lst = myErrorMessage2ValueMap.get(message);
    lst.add(value);
  }

  public boolean isEmpty() {
    return myErrorMessage2ValueMap.isEmpty();
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AllIcons.General.Error;
  }

  @Override
  public void computeChildren(@NotNull XCompositeNode node) {
    XValueChildrenList lst = new XValueChildrenList();
    myErrorMessage2ValueMap.keySet().forEach(s -> lst.addTopGroup(new MyErrorsValueGroup(s)));
    node.addChildren(lst, true);
  }

  private class MyErrorsValueGroup extends XValueGroup {

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
      XValueChildrenList lst = new XValueChildrenList();
      String name = getName();
      myErrorMessage2ValueMap.get(name).forEach(lst::add);
      node.addChildren(lst, true);
    }

    MyErrorsValueGroup(@NotNull String name) {
      super(name);
    }
  }
}
