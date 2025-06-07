// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.structureView;

import com.intellij.navigation.ItemPresentation;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class MarkdownBasePresentation implements ItemPresentation {

  @Override
  public @Nullable Icon getIcon(boolean unused) {
    return PlatformIcons.XML_TAG_ICON;
  }
}
