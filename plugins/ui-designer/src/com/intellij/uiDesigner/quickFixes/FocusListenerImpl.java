// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.quickFixes;

import org.jetbrains.annotations.NotNull;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * [vova] This class should be inner but due to bugs in "beta" generics compiler
 * I need to use "static" modifier.
 */
final class FocusListenerImpl extends FocusAdapter{
  private final QuickFixManager myManager;

  FocusListenerImpl(final @NotNull QuickFixManager manager) {
    myManager = manager;
  }

  @Override
  public void focusGained(final FocusEvent e) {
    if(!e.isTemporary()){
      myManager.updateIntentionHintVisibility();
    }
  }

  @Override
  public void focusLost(final FocusEvent e) {
    if(!(e.isTemporary())){
      myManager.hideIntentionHint();
    }
  }
}
