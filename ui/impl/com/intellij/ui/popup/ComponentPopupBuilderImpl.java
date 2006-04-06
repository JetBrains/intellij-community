/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ui.popup;

import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * User: anna
 * Date: 15-Mar-2006
 */
public class ComponentPopupBuilderImpl implements ComponentPopupBuilder {
  private String myTitle = "";
  private boolean myResizable;
  private boolean myMovable;
  private JComponent myComponent;
  private JComponent myPrefferedFocusedComponent;
  private boolean myRequestFocus;
  private boolean myForceHeavyweight;
  private String myDimensionServiceKey = null;
  private Runnable myCallback = null;


  public ComponentPopupBuilderImpl(final JComponent component,
                                   final JComponent prefferedFocusedComponent) {
    myComponent = component;
    myPrefferedFocusedComponent = prefferedFocusedComponent;
  }

  @NotNull
  public ComponentPopupBuilder setTitle(String title) {
    myTitle = title;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setResizable(final boolean resizable) {
    myResizable = resizable;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setMovable(final boolean movable) {
    myMovable = movable;
    return this;
  }


  @NotNull
  public ComponentPopupBuilder setRequestFocus(final boolean requestFocus) {
    myRequestFocus = requestFocus;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setForceHeavyweight(final boolean forceHeavyweight) {
    myForceHeavyweight = forceHeavyweight;
    return this;
  }

  public ComponentPopupBuilder setDimensionServiceKey(final String dimensionServiceKey) {
    myDimensionServiceKey = dimensionServiceKey;
    return this;
  }

  public ComponentPopupBuilder setCallback(final Runnable runnable) {
    myCallback = runnable;
    return this;
  }

  @NotNull
  public JBPopup createPopup() {
    return new JBPopupImpl(myComponent, myPrefferedFocusedComponent, myRequestFocus, myForceHeavyweight, myDimensionServiceKey, myResizable, myMovable ? (myTitle != null ? myTitle : "") : null, myCallback);
  }
}
