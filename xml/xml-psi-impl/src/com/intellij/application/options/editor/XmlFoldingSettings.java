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
package com.intellij.application.options.editor;

import com.intellij.lang.XmlCodeFoldingSettings;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.BooleanTrackableProperty;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jetbrains.annotations.NotNull;

@State(name = "XmlFoldingSettings", storages = @Storage("editor.codeinsight.xml"))
public class XmlFoldingSettings implements XmlCodeFoldingSettings, PersistentStateComponent<XmlFoldingSettings.State> {
  private final XmlFoldingSettings.State myState = new State();

  public static XmlFoldingSettings getInstance() {
    return ServiceManager.getService(XmlFoldingSettings.class);
  }

  public XmlFoldingSettings() {
    // todo: remove after 2017.1 release
    CssFoldingSettings cssFoldingSettings = CssFoldingSettings.getInstance();
    if (cssFoldingSettings != null) {
      myState.myCollapseDataUri.setValue(cssFoldingSettings.isCollapseDataUri());
    }
  }

  @Override
  public boolean isCollapseXmlTags() {
    return myState.isCollapseXmlTags();
  }

  @Override
  public void setCollapseXmlTags(boolean value) {
    myState.myCollapseXmlTags.setValue(value);
  }

  @Override
  public BooleanTrackableProperty getCollapseXmlTagsProperty() {
    return myState.myCollapseXmlTags;
  }

  @Override
  public boolean isCollapseHtmlStyleAttribute() {
    return myState.isCollapseHtmlStyleAttribute();
  }

  @Override
  public void setCollapseHtmlStyleAttribute(boolean value) {
    myState.myCollapseHtmlStyleAttributes.setValue(value);
  }

  @Override
  public BooleanTrackableProperty getCollapseHtmlStyleAttributeProperty() {
    return myState.myCollapseHtmlStyleAttributes;
  }

  @Override
  public boolean isCollapseEntities() {
    return myState.isCollapseEntities();
  }

  @Override
  public void setCollapseEntities(boolean value) {
    myState.myCollapseEntities.setValue(value);
  }

  @Override
  public BooleanTrackableProperty getCollapseEntitiesProperty() {
    return myState.myCollapseEntities;
  }

  @Override
  public boolean isCollapseDataUri() {
    return myState.isCollapseDataUri();
  }

  @Override
  public void setCollapseDataUri(boolean value) {
    myState.myCollapseDataUri.setValue(value);
  }

  @Override
  public BooleanTrackableProperty getCollapseDataUriProperty() {
    return myState.myCollapseDataUri;
  }

  @Override
  @NotNull
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    XmlSerializerUtil.copyBean(state, myState);
  }

  public static final class State {
    private BooleanTrackableProperty myCollapseXmlTags = new BooleanTrackableProperty();
    private BooleanTrackableProperty myCollapseHtmlStyleAttributes = new BooleanTrackableProperty(true);
    private BooleanTrackableProperty myCollapseEntities = new BooleanTrackableProperty(true);
    private BooleanTrackableProperty myCollapseDataUri = new BooleanTrackableProperty(true);

    @OptionTag("COLLAPSE_XML_TAGS")
    public boolean isCollapseXmlTags() {
      return myCollapseXmlTags.getValue();
    }

    @OptionTag("COLLAPSE_HTML_STYLE_ATTRIBUTE")
    public boolean isCollapseHtmlStyleAttribute() {
      return myCollapseHtmlStyleAttributes.getValue();
    }

    @OptionTag("COLLAPSE_ENTITIES")
    public boolean isCollapseEntities() {
      return myCollapseEntities.getValue();
    }

    @OptionTag("COLLAPSE_DATA_URI")
    public boolean isCollapseDataUri() {
      return myCollapseDataUri.getValue();
    }
  }
}