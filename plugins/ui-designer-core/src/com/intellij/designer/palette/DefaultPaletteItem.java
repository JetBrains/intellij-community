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
package com.intellij.designer.palette;

import com.intellij.designer.model.MetaModel;
import com.intellij.openapi.util.IconLoader;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Alexander Lobas
 */
public class DefaultPaletteItem implements PaletteItem {
  private final String myTitle;
  protected final String myIconPath;
  protected Icon myIcon;
  private final String myTooltip;
  private final String myVersion;
  private boolean myEnabled = true;
  private final String myDeprecatedVersion;
  private final String myDeprecatedHint;

  protected MetaModel myMetaModel;

  public DefaultPaletteItem(Element palette) {
    this(palette.getAttributeValue("title"),
         palette.getAttributeValue("icon"),
         palette.getAttributeValue("tooltip"),
         palette.getAttributeValue("version"),
         palette.getAttributeValue("deprecated"),
         palette.getAttributeValue("deprecatedHint"));
  }

  public DefaultPaletteItem(String title,
                            String iconPath,
                            String tooltip,
                            String version,
                            String deprecatedVersion,
                            String deprecatedHint) {
    myTitle = title;
    myIconPath = iconPath;
    myTooltip = tooltip;
    myVersion = version;
    myDeprecatedVersion = deprecatedVersion;
    myDeprecatedHint = deprecatedHint;
  }

  @Override
  public String getTitle() {
    return myTitle;
  }

  @Override
  public Icon getIcon() {
    if (myIcon == null) {
      myIcon = IconLoader.findIcon(myIconPath, myMetaModel.getModel());
    }
    return myIcon;
  }

  @Override
  public String getTooltip() {
    return myTooltip;
  }

  @Override
  public String getVersion() {
    return myVersion;
  }

  @Override
  public boolean isEnabled() {
    return myEnabled;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  @Nullable
  @Override
  public String getDeprecatedIn() {
    return myDeprecatedVersion;
  }

  @Nullable
  @Override
  public String getDeprecatedHint() {
    return myDeprecatedHint;
  }

  @Override
  public String getCreation() {
    return myMetaModel.getCreation();
  }

  @Override
  public MetaModel getMetaModel() {
    return myMetaModel;
  }

  @Override
  public void setMetaModel(MetaModel metaModel) {
    myMetaModel = metaModel;
  }
}