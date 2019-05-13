/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.model;

import com.intellij.designer.palette.DefaultPaletteItem;
import com.intellij.designer.palette.PaletteItem;
import com.intellij.designer.propertyTable.IPropertyDecorator;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class MetaModel {
  private final Class<RadComponent> myModel;
  private Class<RadLayout> myLayout;
  private final String myTarget;
  private final String myTag;
  private PaletteItem myPaletteItem;
  private String myTitle;
  private String myIconPath;
  private Icon myIcon;
  private String myCreation;
  private boolean myDelete = true;
  private List<String> myInplaceProperties = Collections.emptyList();
  private List<String> myTopProperties = Collections.emptyList();
  private List<String> myNormalProperties = Collections.emptyList();
  private List<String> myImportantProperties = Collections.emptyList();
  private List<String> myExpertProperties = Collections.emptyList();
  private List<String> myDeprecatedProperties = Collections.emptyList();
  private List<MetaModel> myMorphingModels = Collections.emptyList();

  public MetaModel(Class<RadComponent> model, String target, String tag) {
    myModel = model;
    myTarget = target;
    myTag = tag;
  }

  public Class<RadComponent> getModel() {
    return myModel;
  }

  public Class<RadLayout> getLayout() {
    return myLayout;
  }

  public void setLayout(Class<RadLayout> layout) {
    myLayout = layout;
  }

  public String getTarget() {
    return myTarget;
  }

  public String getTag() {
    return myTag;
  }

  public boolean isTag(@NotNull String tag) {
    return tag.equals(myTag);
  }

  public boolean isTag(String... tags) {
    return ArrayUtil.contains(myTag, tags);
  }

  public String getCreation() {
    return myCreation;
  }

  public void setCreation(String creation) {
    myCreation = creation;
  }

  public boolean canDelete() {
    return myDelete;
  }

  public void setDelete(boolean delete) {
    myDelete = delete;
  }

  public String getTitle() {
    return myTitle;
  }

  public Icon getIcon() {
    if (myIcon == null) {
      if (myIconPath == null) {
        return myPaletteItem == null ? null : myPaletteItem.getIcon();
      }
      myIcon = IconLoader.findIcon(myIconPath, myModel);
    }
    return myIcon;
  }

  public void setPresentation(String title, String iconPath) {
    myTitle = title;
    myIconPath = iconPath;
    myIcon = null;
  }

  public PaletteItem getPaletteItem() {
    return myPaletteItem;
  }

  public void setPaletteItem(@NotNull DefaultPaletteItem paletteItem) {
    myPaletteItem = paletteItem;
    myPaletteItem.setMetaModel(this);
  }

  public List<MetaModel> getMorphingModels() {
    return myMorphingModels;
  }

  public void setMorphingModels(List<MetaModel> morphingModels) {
    myMorphingModels = morphingModels;
  }

  public List<String> getInplaceProperties() {
    return myInplaceProperties;
  }

  public void setInplaceProperties(List<String> inplaceProperties) {
    myInplaceProperties = inplaceProperties;
  }

  public List<String> getTopProperties() {
    return myTopProperties;
  }

  public void setTopProperties(List<String> topProperties) {
    myTopProperties = topProperties;
  }

  public void setNormalProperties(List<String> normalProperties) {
    myNormalProperties = normalProperties;
  }

  public boolean isImportantProperty(String name) {
    return myImportantProperties.contains(name);
  }

  public void setImportantProperties(List<String> importantProperties) {
    myImportantProperties = importantProperties;
  }

  public boolean isExpertProperty(String name) {
    return myExpertProperties.contains(name);
  }

  public void setExpertProperties(List<String> expertProperties) {
    myExpertProperties = expertProperties;
  }

  public boolean isDeprecatedProperty(String name) {
    return myDeprecatedProperties.contains(name);
  }

  public void setDeprecatedProperties(List<String> deprecatedProperties) {
    myDeprecatedProperties = deprecatedProperties;
  }

  public void decorate0(Property property, String name) {
    property.setImportant(isImportantProperty(name));
    property.setExpert(isExpertProperty(name));
    property.setDeprecated(isDeprecatedProperty(name));
  }

  public void decorate(Property property, String name) {
    decorate0(property, name);

    if (property instanceof IPropertyDecorator) {
      ((IPropertyDecorator)property).decorate(this);
    }
  }

  public Property decorateWithOverride(Property property) {
    String name = property.getName();

    if (myNormalProperties.contains(name) ||
        myImportantProperties.contains(name) ||
        myExpertProperties.contains(name) ||
        myDeprecatedProperties.contains(name)) {
      property = property.createForNewPresentation();
      decorate(property, name);
    }

    return property;
  }
}