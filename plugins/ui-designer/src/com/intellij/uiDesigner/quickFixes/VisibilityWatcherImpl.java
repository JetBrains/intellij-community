// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.quickFixes;

import com.intellij.openapi.wm.impl.VisibilityWatcher;

import javax.swing.*;

/**
 * [vova] This class should be inner but due to bugs in "beta" generics compiler
 * I need to use "static" modifier.
 */
final class VisibilityWatcherImpl extends VisibilityWatcher{
  private final QuickFixManager myManager;
  private final JComponent myComponent;

  VisibilityWatcherImpl(final QuickFixManager manager, final JComponent component) {
    myManager = manager;
    myComponent = component;
  }

  @Override
  public void visibilityChanged() {
    if(myComponent.isShowing()){
      myManager.updateIntentionHintVisibility();
    }
    else{
      myManager.hideIntentionHint();
    }
  }
}
