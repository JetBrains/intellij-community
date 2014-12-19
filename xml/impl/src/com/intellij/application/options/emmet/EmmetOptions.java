/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.application.options.emmet;

import com.intellij.codeInsight.template.emmet.filters.ZenCodingFilter;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.components.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@State(
  name = "EmmetOptions",
  storages = @Storage(file = StoragePathMacros.APP_CONFIG + "/emmet.xml")
)
public class EmmetOptions implements PersistentStateComponent<EmmetOptions> {
  private boolean myEmmetEnabled = true;
  private int myEmmetExpandShortcut = TemplateSettings.TAB_CHAR;
  private boolean myPreviewEnabled = false;
  private boolean myCompactBooleanAllowed = true;
  private Set<String> myBooleanAttributes = ContainerUtil.newHashSet("contenteditable", "seamless");
  private Set<String> myFiltersEnabledByDefault = ContainerUtil.newHashSet();

  @NotNull
  public Set<String> getFiltersEnabledByDefault() {
    return myFiltersEnabledByDefault;
  }

  public void setFiltersEnabledByDefault(@NotNull Set<String> filtersEnabledByDefault) {
    myFiltersEnabledByDefault = filtersEnabledByDefault;
  }

  public boolean isFilterEnabledByDefault(@NotNull ZenCodingFilter filter) {
    return myFiltersEnabledByDefault.contains(filter.getSuffix());
  }

  public void setEmmetExpandShortcut(int emmetExpandShortcut) {
    myEmmetExpandShortcut = emmetExpandShortcut;
  }

  public int getEmmetExpandShortcut() {
    return myEmmetExpandShortcut;
  }

  public boolean isPreviewEnabled() {
    return myPreviewEnabled;
  }

  public void setPreviewEnabled(boolean previewEnabled) {
    myPreviewEnabled = previewEnabled;
  }

  public boolean isCompactBooleanAllowed() {
    return myCompactBooleanAllowed;
  }

  public void setCompactBooleanAllowed(boolean compactBooleanAllowed) {
    myCompactBooleanAllowed = compactBooleanAllowed;
  }

  public Set<String> getBooleanAttributes() {
    return myBooleanAttributes;
  }

  public void setBooleanAttributes(@NotNull Set<String> booleanAttributes) {
    myBooleanAttributes = booleanAttributes;
  }

  public boolean isEmmetEnabled() {
    return myEmmetEnabled;
  }

  public void setEmmetEnabled(boolean emmetEnabled) {
    myEmmetEnabled = emmetEnabled;
  }

  @Nullable
  @Override
  public EmmetOptions getState() {
    return this;
  }

  @Override
  public void loadState(final EmmetOptions state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static EmmetOptions getInstance() {
    return ServiceManager.getService(EmmetOptions.class);
  }
}
