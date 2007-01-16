/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.module.Module;
import com.intellij.ui.SimpleTextAttributes;

/**
 * @author yole
 */
public class ChangesBrowserModuleNode extends ChangesBrowserNode {
  protected ChangesBrowserModuleNode(Module userObject) {
    super(userObject);
  }

  @Override
  public void render(final ChangesBrowserNodeRenderer renderer, final boolean selected, final boolean expanded, final boolean hasFocus) {
    final Module module = (Module)userObject;

    renderer.append(module.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    appendCount(renderer);
    renderer.setIcon(module.getModuleType().getNodeIcon(expanded));
  }
}