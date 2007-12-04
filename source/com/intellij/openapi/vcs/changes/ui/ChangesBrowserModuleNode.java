/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.module.Module;
import com.intellij.ui.SimpleTextAttributes;

/**
 * @author yole
 */
public class ChangesBrowserModuleNode extends ChangesBrowserNode<Module> {
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

  @Override
  public String getTextPresentation() {
    return getUserObject().getName();
  }

  public int getSortWeight() {
    return 3;
  }

  public int compareUserObjects(final Object o2) {
    if (o2 instanceof Module) {
      return getUserObject().getName().compareToIgnoreCase(((Module) o2).getName());
    }

    return 0;
  }
}