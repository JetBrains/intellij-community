// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SimpleConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.util.SmartList;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.xdebugger.settings.DebuggerSettingsCategory;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import com.jetbrains.python.debugger.PyDebugValue;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.singletonList;

public final class PyDebuggerSettings extends XDebuggerSettings<PyDebuggerSettings> {
  private boolean myLibrariesFilterEnabled;
  private boolean mySteppingFiltersEnabled;
  private @NotNull List<PySteppingFilter> mySteppingFilters;
  public static final String FILTERS_DIVIDER = ";";
  private boolean myWatchReturnValues = false;
  private boolean mySimplifiedView = true;
  private volatile PyDebugValue.ValuesPolicy myValuesPolicy = PyDebugValue.ValuesPolicy.ASYNC;
  private boolean myAlwaysDoSmartStepIntoEnabled = true;

  public PyDebuggerSettings() {
    super("python");
    mySteppingFilters = new SmartList<>();
  }

  public boolean isWatchReturnValues() {
    return myWatchReturnValues;
  }

  public void setWatchReturnValues(boolean watchReturnValues) {
    myWatchReturnValues = watchReturnValues;
  }

  public boolean isSimplifiedView() {
    return mySimplifiedView;
  }

  public void setSimplifiedView(boolean simplifiedView) {
    mySimplifiedView = simplifiedView;
  }

  public PyDebugValue.ValuesPolicy getValuesPolicy() {
    return myValuesPolicy;
  }

  public void setValuesPolicy(PyDebugValue.ValuesPolicy valuesPolicy) {
    myValuesPolicy = valuesPolicy;
  }

  public static PyDebuggerSettings getInstance() {
    return getInstance(PyDebuggerSettings.class);
  }

  public boolean isLibrariesFilterEnabled() {
    return myLibrariesFilterEnabled;
  }

  public void setLibrariesFilterEnabled(boolean librariesFilterEnabled) {
    myLibrariesFilterEnabled = librariesFilterEnabled;
  }

  public boolean isSteppingFiltersEnabled() {
    return mySteppingFiltersEnabled;
  }

  public void setSteppingFiltersEnabled(boolean steppingFiltersEnabled) {
    mySteppingFiltersEnabled = steppingFiltersEnabled;
  }

  public void setAlwaysDoSmartStepIntoEnabled(boolean alwaysDoSmartStepIntoEnabled) {
    myAlwaysDoSmartStepIntoEnabled = alwaysDoSmartStepIntoEnabled;
  }

  public boolean isAlwaysDoSmartStepInto() {
    return myAlwaysDoSmartStepIntoEnabled;
  }

  @NotNull
  public List<PySteppingFilter> getSteppingFilters() {
    return mySteppingFilters;
  }

  @NotNull
  public String getSteppingFiltersForProject(@NotNull Project project) {
    StringBuilder sb = new StringBuilder();
    for (PySteppingFilter filter : mySteppingFilters) {
      if (filter.isEnabled()) {
        sb.append(filter.getAbsolutePlatformIndependentFilter(project)).append(FILTERS_DIVIDER);
      }
    }
    return sb.toString();
  }

  public void setSteppingFilters(@NotNull List<PySteppingFilter> steppingFilters) {
    mySteppingFilters = steppingFilters;
  }

  @Override
  public @NotNull PyDebuggerSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull PyDebuggerSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Override
  public boolean isTargetedToProduct(@NotNull Configurable configurable) {
    return true;
  }

  @NotNull
  @Override
  public Collection<? extends Configurable> createConfigurables(@NotNull DebuggerSettingsCategory category) {
    if (category == DebuggerSettingsCategory.STEPPING) {
      return singletonList(SimpleConfigurable.create("python.debug.configurable", "Python", //NON-NLS
                                                     PyDebuggerSteppingConfigurableUi.class, () -> this));
    }
    return Collections.emptyList();
  }
}
