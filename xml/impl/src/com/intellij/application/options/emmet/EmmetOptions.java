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

import com.google.common.collect.Sets;
import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.xml.XmlBundle;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.io.Resources.getResource;

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
  private boolean myEmmetEnabled = WebEditorOptions.getInstance().isZenCodingEnabled();
  private int myEmmetExpandShortcut = WebEditorOptions.getInstance().getZenCodingExpandShortcut();
  private boolean myFuzzySearchEnabled = true;
  private boolean myAutoInsertCssPrefixedEnabled = true;
  @Nullable
  private Map<String, Integer> prefixes = null;

  public void setPrefixInfo(Collection<CssPrefixInfo> prefixInfos) {
    prefixes = newHashMap();
    for (CssPrefixInfo state : prefixInfos) {
      prefixes.put(state.getPropertyName(), state.toIntegerValue());
    }
  }

  public CssPrefixInfo getPrefixStateForProperty(String propertyName) {
    return CssPrefixInfo.fromIntegerValue(propertyName, getPrefixes().get(propertyName));
  }

  public Set<CssPrefixInfo> getAllPrefixInfo() {
    Set<CssPrefixInfo> result = Sets.newHashSetWithExpectedSize(getPrefixes().size());
    for (Map.Entry<String, Integer> entry : getPrefixes().entrySet()) {
      result.add(CssPrefixInfo.fromIntegerValue(entry.getKey(), entry.getValue()));
    }
    return result;
  }

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

  public boolean isEmmetEnabled() {
    return myEmmetEnabled;
  }

  public void setEmmetEnabled(boolean emmetEnabled) {
    myEmmetEnabled = emmetEnabled;
  }

  public boolean isAutoInsertCssPrefixedEnabled() {
    return myAutoInsertCssPrefixedEnabled;
  }

  public void setAutoInsertCssPrefixedEnabled(boolean autoInsertCssPrefixedEnabled) {
    myAutoInsertCssPrefixedEnabled = autoInsertCssPrefixedEnabled;
  }

  public void setFuzzySearchEnabled(boolean fuzzySearchEnabled) {
    myFuzzySearchEnabled = fuzzySearchEnabled;
  }

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

  public void loadState(final EmmetOptions state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static EmmetOptions getInstance() {
    return ServiceManager.getService(EmmetOptions.class);
  }

  @NotNull
  public Map<String, Integer> getPrefixes() {
    if (prefixes == null) {
      prefixes = loadDefaultPrefixes();
    }
    return prefixes;
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setPrefixes(@Nullable Map<String, Integer> prefixes) {
    this.prefixes = prefixes;
  }

  public Map<String, Integer> loadDefaultPrefixes() {
    Map<String, Integer> result = newHashMap();
    try {
      Document document = JDOMUtil.loadDocument(getResource(EmmetOptions.class, "emmet_default_options.xml"));
      Element prefixesElement = document.getRootElement().getChild("prefixes");
      if (prefixesElement != null) {
        for (Object entry : prefixesElement.getChildren("entry")) {
          Element entryElement = (Element)entry;
          String propertyName = entryElement.getAttributeValue("key");
          Integer value = StringUtil.parseInt(entryElement.getAttributeValue("value"), 0);
          result.put(propertyName, value);
        }
      }
    }
    catch (Exception e) {
      Logger.getInstance(EmmetOptions.class).warn(e);
      return result;
    }
    return result;
  }
}
