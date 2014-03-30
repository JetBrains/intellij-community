/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

/**
 * User: zolotov
 * Date: 2/20/13
 */
@State(
  name = "EmmetOptions",
  storages = {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/emmet.xml"
    )}
)
public class EmmetOptions implements PersistentStateComponent<EmmetOptions>, ExportableComponent {
  private boolean myBemFilterEnabledByDefault = false;
  private boolean myEmmetEnabled = true;
  private int myEmmetExpandShortcut = TemplateSettings.TAB_CHAR;
  private boolean myFuzzySearchEnabled = true;
  private boolean myAutoInsertCssPrefixedEnabled = true;
  private boolean myPreviewEnabled = false;
  @NotNull
  private Map<String, Integer> prefixes = ContainerUtil.newHashMap();


  public boolean isBemFilterEnabledByDefault() {
    return myBemFilterEnabledByDefault;
  }

  public void setBemFilterEnabledByDefault(boolean enableBemFilterByDefault) {
    myBemFilterEnabledByDefault = enableBemFilterByDefault;
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

  @Deprecated
  //use {@link CssEmmetOptions}
  public boolean isAutoInsertCssPrefixedEnabled() {
    return myAutoInsertCssPrefixedEnabled;
  }

  @Deprecated
  //use {@link CssEmmetOptions}
  public void setAutoInsertCssPrefixedEnabled(boolean autoInsertCssPrefixedEnabled) {
    myAutoInsertCssPrefixedEnabled = autoInsertCssPrefixedEnabled;
  }

  @Deprecated
  //use {@link CssEmmetOptions}
  public void setFuzzySearchEnabled(boolean fuzzySearchEnabled) {
    myFuzzySearchEnabled = fuzzySearchEnabled;
  }

  @Deprecated
  //use {@link CssEmmetOptions}
  public boolean isFuzzySearchEnabled() {
    return myFuzzySearchEnabled;
  }

  @NotNull
  @Override
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile("emmet")};
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return XmlBundle.message("emmet.configuration.title");
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

  @NotNull
  @Deprecated
  //use {@link CssEmmetOptions}
  public Map<String, Integer> getPrefixes() {
    return prefixes;
  }

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  //use {@link CssEmmetOptions}
  public void setPrefixes(@NotNull Map<String, Integer> prefixes) {
    this.prefixes = prefixes;
  }
}
