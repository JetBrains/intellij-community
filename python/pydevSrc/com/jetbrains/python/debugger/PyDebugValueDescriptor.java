// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.jetbrains.python.debugger.render.PyNodeRenderer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class PyDebugValueDescriptor {

  @Nullable private PyNodeRenderer myRenderer;

  /**
   * Because of the variables and recreated every time we make a step in the debugger,
   * we have to save the variable children's descriptors to restore them when updating
   * the debugger variable view. Note that even if there is no renderer associated
   * with a {@link PyDebugValue} instance, it still maintains a value descriptor
   * to preserve access to its children renderers. Otherwise, it will be impossible to
   * reach them during the renderers' restoration process.
   */
  @Nullable private Map<String, PyDebugValueDescriptor> myChildrenDescriptors;

  @Nullable
  public PyNodeRenderer getRenderer() {
    return myRenderer;
  }

  public void setRenderer(@Nullable PyNodeRenderer renderer) {
    myRenderer = renderer;
  }

  @Nullable
  public Map<String, PyDebugValueDescriptor> getChildrenDescriptors() {
    return myChildrenDescriptors;
  }

  public void setChildrenDescriptors(@Nullable Map<String, PyDebugValueDescriptor> childrenDescriptors) {
    myChildrenDescriptors = childrenDescriptors;
  }
}
