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

import com.intellij.designer.palette.Item;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Alexander Lobas
 */
public class MetaModel {
  private final Class<RadComponent> myModel;
  private Class<RadLayout> myLayout;
  private final String myTarget;
  private final String myTag;
  private Item myPaletteItem;
  private String myTitle;
  private String myIconPath;
  private Icon myIcon;
  private String myCreation;

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

  public String getCreation() {
    return myCreation;
  }

  public void setCreation(String creation) {
    myCreation = creation;
  }

  public String getTitle() {
    return myTitle;
  }

  public Icon getIcon() {
    if (myIcon == null) {
      if (myIconPath == null) {
        return myPaletteItem.getIcon();
      }
      myIcon = IconLoader.getIcon(myIconPath);
    }
    return myIcon;
  }

  public void setPresentation(String title, String iconPath) {
    myTitle = title;
    myIconPath = iconPath;
    myIcon = null;
  }

  public Item getPaletteItem() {
    return myPaletteItem;
  }

  public void setPaletteItem(@NotNull Item paletteItem) {
    myPaletteItem = paletteItem;
    myPaletteItem.setMetaModel(this);
  }
}