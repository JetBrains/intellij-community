package org.intellij.plugins.markdown.structureView;

import com.intellij.navigation.ItemPresentation;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class MarkdownBasePresentation implements ItemPresentation {

  @Nullable
  @Override
  public Icon getIcon(boolean unused) {
    return PlatformIcons.XML_TAG_ICON;
  }
}
