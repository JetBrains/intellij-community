// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.jetbrains.python.debugger.render.PyNodeRenderer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class PyDebugValueDescriptor {

  private @Nullable PyNodeRenderer myRenderer;

  /**
   * Because of the variables and recreated every time we make a step in the debugger,
   * we have to save the variable children's descriptors to restore them when updating
   * the debugger variable view. Note that even if there is no renderer associated
   * with a {@link PyDebugValue} instance, it still maintains a value descriptor
   * to preserve access to its children renderers. Otherwise, it will be impossible to
   * reach them during the renderers' restoration process.
   */
  private @Nullable Map<String, PyDebugValueDescriptor> myChildrenDescriptors;

  public @Nullable PyNodeRenderer getRenderer() {
    return myRenderer;
  }

  public void setRenderer(@Nullable PyNodeRenderer renderer) {
    myRenderer = renderer;
  }

  public @Nullable Map<String, PyDebugValueDescriptor> getChildrenDescriptors() {
    return myChildrenDescriptors;
  }

  public void setChildrenDescriptors(@Nullable Map<String, PyDebugValueDescriptor> childrenDescriptors) {
    myChildrenDescriptors = childrenDescriptors;
  }
}
