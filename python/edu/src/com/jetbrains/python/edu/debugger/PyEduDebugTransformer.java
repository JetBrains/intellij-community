package com.jetbrains.python.edu.debugger;

import com.intellij.util.containers.hash.HashMap;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.debugger.PyDebugValueTransformer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class PyEduDebugTransformer implements PyDebugValueTransformer {
  @Override
  public XValueChildrenList getTransformedChildren(@NotNull XValueChildrenList children) {
    XValueChildrenList list = new XValueChildrenList();
    Map<String, XValue> magicValues = new HashMap<String, XValue>();
    for (int i = 0; i < children.size(); i++) {
      String name = children.getName(i);
      XValue value = children.getValue(i);
      if (name.startsWith("__") && name.endsWith("__")) {
        magicValues.put(name, value);
      }
      else {
        list.add(name, value);
      }
    }
    if (!magicValues.isEmpty()) {
      list.add(new PyEduMagicDebugValue("magic variables", magicValues));
    }
    return list;
  }
}
