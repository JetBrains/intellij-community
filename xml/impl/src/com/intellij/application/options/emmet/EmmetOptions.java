/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@State(
  name = "EmmetOptions",
  storages = @Storage("emmet.xml")
)
public class EmmetOptions implements PersistentStateComponent<EmmetOptions> {
  private boolean myEmmetEnabled = true;
  private int myEmmetExpandShortcut = TemplateSettings.TAB_CHAR;
  private boolean myPreviewEnabled = true;
  @NotNull
  private Set<String> myFiltersEnabledByDefault = ContainerUtil.newHashSet();
  private boolean myHrefAutoDetectEnabled = true;
  private boolean myAddEditPointAtTheEndOfTemplate = false;

  @NotNull private String myBemElementSeparator = "__";
  @NotNull private String myBemModifierSeparator = "_";
  @NotNull private String myBemShortElementPrefix = "-";

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

  public boolean isEmmetEnabled() {
    return myEmmetEnabled;
  }

  public void setEmmetEnabled(boolean emmetEnabled) {
    myEmmetEnabled = emmetEnabled;
  }

  public void setHrefAutoDetectEnabled(boolean hrefAutoDetectEnabled) {
    myHrefAutoDetectEnabled = hrefAutoDetectEnabled;
  }

  public boolean isHrefAutoDetectEnabled() {
    return myHrefAutoDetectEnabled;
  }

  public boolean isAddEditPointAtTheEndOfTemplate() {
    return myAddEditPointAtTheEndOfTemplate;
  }

  public void setAddEditPointAtTheEndOfTemplate(boolean addEditPointAtTheEndOfTemplate) {
    myAddEditPointAtTheEndOfTemplate = addEditPointAtTheEndOfTemplate;
  }

  @NotNull
  public String getBemElementSeparator() {
    return myBemElementSeparator;
  }

  public void setBemElementSeparator(@Nullable String bemElementSeparator) {
    myBemElementSeparator = StringUtil.notNullize(bemElementSeparator);
  }

  @NotNull
  public String getBemModifierSeparator() {
    return myBemModifierSeparator;
  }

  public void setBemModifierSeparator(@Nullable String bemModifierSeparator) {
    myBemModifierSeparator = StringUtil.notNullize(bemModifierSeparator);
  }

  @NotNull
  public String getBemShortElementPrefix() {
    return myBemShortElementPrefix;
  }

  public void setBemShortElementPrefix(@Nullable String bemShortElementPrefix) {
    myBemShortElementPrefix = StringUtil.notNullize(bemShortElementPrefix);
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
