/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.ui.search;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.GlassPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: 17-Mar-2006
 */
public class DefaultSearchableConfigurable implements Configurable {
  private SearchableConfigurable myDelegate;
  private GlassPanel myGlassPanel;
  private JComponent myComponent;

  public DefaultSearchableConfigurable(final SearchableConfigurable delegate) {
    myDelegate = delegate;
  }

  @NonNls
  public String getId() {
    return myDelegate.getId();
  }

  public void clearSearch() {
    if (myGlassPanel == null) return; // Not yet initialized.
    if (!myDelegate.clearSearch()){
      myGlassPanel.clear();
    }
  }

  public void enableSearch(String option) {
    Runnable runnable = myDelegate.enableSearch(option);
    if (runnable == null){
      runnable = SearchUtil.lightOptions(myDelegate, myComponent, option, myGlassPanel);
    }
    runnable.run();
  }

  public String getDisplayName() {
    return myDelegate.getDisplayName();
  }

  public Icon getIcon() {
    return myDelegate.getIcon();
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return myDelegate.getHelpTopic();
  }

  public JComponent createComponent() {
    myComponent = myDelegate.createComponent();
    myGlassPanel = new GlassPanel(myComponent);
    return myComponent;
  }

  public boolean isModified() {
    return myDelegate.isModified();
  }

  public void apply() throws ConfigurationException {
    myDelegate.apply();
  }

  public void reset() {
    if (myComponent != null) {
      myComponent.getRootPane().setGlassPane(myGlassPanel);
    }
    myDelegate.reset();
  }

  public void disposeUIResources() {
    myGlassPanel = null;
    myComponent = null;
    myDelegate.disposeUIResources();
  }

  public Configurable getDelegate() {
    return myDelegate;
  }
}
