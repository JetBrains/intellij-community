package com.intellij.debugger.settings;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;

import javax.swing.*;
import java.util.Iterator;
import java.util.List;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public abstract class CompositeConfigurable extends BaseConfigurable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.settings.DebuggerConfigurable");

  private JTabbedPane myTabbedPane;
  private List<Configurable> myConfigurables;

  public void reset() {
    for (Iterator<Configurable> iterator = getConfigurables().iterator(); iterator.hasNext();) {
      Configurable configurable = iterator.next();
      configurable.reset();
    }
  }

  public void apply() throws ConfigurationException {
    for (Iterator<Configurable> iterator = getConfigurables().iterator(); iterator.hasNext();) {
      Configurable configurable = iterator.next();
      configurable.apply();
    }
  }

  public boolean isModified() {
    for (Iterator<Configurable> iterator = getConfigurables().iterator(); iterator.hasNext();) {
      Configurable configurable = iterator.next();
      if(configurable.isModified()) return true;
    }
    return false;
  }

  public JComponent createComponent() {
    myTabbedPane = new JTabbedPane();
    for (Iterator<Configurable> iterator = getConfigurables().iterator(); iterator.hasNext();) {
      Configurable configurable = iterator.next();
      myTabbedPane.addTab(configurable.getDisplayName(), configurable.getIcon(), configurable.createComponent());
    }
    return myTabbedPane;
  }

  public void disposeUIResources() {
    myTabbedPane = null;
    myConfigurables = null;
  }

  protected abstract List<Configurable> createConfigurables();

  private List<Configurable> getConfigurables() {
    if (myConfigurables == null) {
      myConfigurables = createConfigurables();
    }
    return myConfigurables;
  }
}
