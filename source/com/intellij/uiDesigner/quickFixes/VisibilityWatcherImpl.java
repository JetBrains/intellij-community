package com.intellij.uiDesigner.quickFixes;

import com.intellij.openapi.wm.impl.VisibilityWatcher;

import javax.swing.*;

/**
 * [vova] This class should be inner but due to bugs in "beta" generics compiler
 * I need to use "static" modifier.
 * 
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class VisibilityWatcherImpl extends VisibilityWatcher{
  private final QuickFixManager myManager;
  private final JComponent myComponent;

  public VisibilityWatcherImpl(final QuickFixManager manager, final JComponent component) {
    myManager = manager;
    myComponent = component;
  }

  public void visibilityChanged() {
    if(myComponent.isShowing()){
      myManager.updateIntentionHintVisibility();
    }
    else{
      myManager.hideIntentionHint();
    }
  }
}
