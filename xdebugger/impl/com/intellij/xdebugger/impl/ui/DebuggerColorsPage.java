/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.util.IconLoader;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * @author max
 */
public class DebuggerColorsPage implements ColorSettingsPage {
  @NotNull
  public String getDisplayName() {
    return XDebuggerBundle.message("xdebugger.colors.page.name");
  }

  @Nullable
  public Icon getIcon() {
    return IconLoader.getIcon("/actions/startDebugger.png");
  }

  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return new AttributesDescriptor[] {
      new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.breakpoint.line"), DebuggerColors.BREAKPOINT_ATTRIBUTES),
      new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.execution.point"), DebuggerColors.EXECUTIONPOINT_ATTRIBUTES),
    };
  }

  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    return new ColorDescriptor[] {
      new ColorDescriptor(OptionsBundle.message("options.java.attribute.descriptor.recursive.call"), DebuggerColors.RECURSIVE_CALL_ATTRIBUTES, ColorDescriptor.Kind.BACKGROUND)
    };
  }

  @NotNull
  public SyntaxHighlighter getHighlighter() {
    return new PlainSyntaxHighlighter();
  }

  @NonNls
  @NotNull
  public String getDemoText() {
    return " ";
  }

  @Nullable
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return null;
  }
}
