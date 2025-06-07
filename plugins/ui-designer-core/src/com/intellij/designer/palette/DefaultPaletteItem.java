// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer.palette;

import com.intellij.designer.model.MetaModel;
import com.intellij.openapi.util.IconLoader;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Alexander Lobas
 */
public class DefaultPaletteItem implements PaletteItem {
  private final @NotNull String myTitle;
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

  public DefaultPaletteItem(@NotNull String title,
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
  public @NotNull String getTitle() {
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

  @Override
  public @Nullable String getDeprecatedIn() {
    return myDeprecatedVersion;
  }

  @Override
  public @Nullable String getDeprecatedHint() {
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