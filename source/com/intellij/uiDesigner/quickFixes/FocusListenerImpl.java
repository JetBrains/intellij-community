package com.intellij.uiDesigner.quickFixes;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * [vova] This class should be inner but due to bugs in "beta" generics compiler
 * I need to use "static" modifier.
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class FocusListenerImpl extends FocusAdapter{
  private final QuickFixManager myManager;

  public FocusListenerImpl(final QuickFixManager manager) {
    if(manager == null){
      throw new IllegalArgumentException();
    }
    myManager = manager;
  }

  public void focusGained(final FocusEvent e) {
    if(!e.isTemporary()){
      myManager.updateIntentionHintVisibility();
    }
  }

  public void focusLost(final FocusEvent e) {
    if(!(e.isTemporary())){
      myManager.hideIntentionHint();
    }
  }
}
