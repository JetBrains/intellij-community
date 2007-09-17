/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ui.popup;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.PsiElement;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Set;
import java.util.LinkedHashSet;

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
  private Computable<Boolean> myCallback = null;
  private Condition<PsiElement> myPopupUpdater;
  private Project myProject;
  private boolean myCancelOnClickOutside = true;
  private Set<JBPopupListener> myListeners = new LinkedHashSet<JBPopupListener>();
  private boolean myUseDimSevriceForXYLocation;

  private IconButton myCancelButton;
  private MouseChecker myCancelOnMouseOutCallback;
  private boolean myCancelOnWindow;
  private ActiveIcon myTitleIcon = new ActiveIcon(new EmptyIcon(0));
  private boolean myCancelKeyEnabled = true;
  private boolean myLocateByContent = false;
  private boolean myPlacewithinScreen = true;
  private Dimension myMinSize;


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
  public ComponentPopupBuilder setCancelOnClickOutside(final boolean cancel) {
    myCancelOnClickOutside = cancel;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setCancelOnMouseOutCallback(final MouseChecker shouldCancel) {
    myCancelOnMouseOutCallback = shouldCancel;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder addListener(final JBPopupListener listener) {
    myListeners.add(listener);
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

  @NotNull
  public ComponentPopupBuilder setDimensionServiceKey(final Project project, final String dimensionServiceKey, final boolean useForXYLocation) {
    myDimensionServiceKey = dimensionServiceKey;
    myUseDimSevriceForXYLocation = useForXYLocation;
    myProject = project;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setCancelCallback(final Computable<Boolean> shouldProceed) {
    myCallback = shouldProceed;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setCancelButton(final IconButton cancelButton) {
    myCancelButton = cancelButton;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setCancelOnOtherWindowOpen(final boolean cancelOnWindow) {
    myCancelOnWindow = cancelOnWindow;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setLookupAndSearchUpdater(final Condition<PsiElement> updater, Project project) {
    myPopupUpdater = updater;
    myProject = project;
    return this;
  }

  @NotNull
  public JBPopup createPopup() {
    final JBPopupImpl popup = new JBPopupImpl(myProject, myComponent, myPrefferedFocusedComponent, myRequestFocus, myForceHeavyweight,
                                              myDimensionServiceKey, myResizable, myMovable ? (myTitle != null ? myTitle : "") : null,
                                              myCallback, myCancelOnClickOutside, myListeners, myUseDimSevriceForXYLocation, myCancelButton,
                                              myCancelOnMouseOutCallback, myCancelOnWindow, myTitleIcon, myCancelKeyEnabled, myLocateByContent,
                                              myPlacewithinScreen, myMinSize);
    if (myPopupUpdater != null) {
      popup.setPopupUpdater(myPopupUpdater, myProject);
    }
    return popup;
  }

  @NotNull
  public ComponentPopupBuilder setRequestFocusIfNotLookupOrSearch(Project project) {
    final Component focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(project);
    boolean fromQuickSearch =  focusedComponent != null && focusedComponent.getParent() instanceof ChooseByNameBase.JPanelProvider;
    myRequestFocus = !fromQuickSearch && LookupManager.getInstance(project).getActiveLookup() == null;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setTitleIcon(@NotNull final ActiveIcon icon) {
    myTitleIcon = icon;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setCancelKeyEnabled(final boolean enabled) {
    myCancelKeyEnabled = enabled;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setLocateByContent(final boolean byContent) {
    myLocateByContent = byContent;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setLocateWithinScreenBounds(final boolean within) {
    myPlacewithinScreen = within;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setMinSize(final Dimension minSize) {
    myMinSize = minSize;
    return this;
  }
}
