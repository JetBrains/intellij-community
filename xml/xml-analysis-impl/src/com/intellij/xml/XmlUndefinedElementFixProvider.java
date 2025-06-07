// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class XmlUndefinedElementFixProvider {
  public static final ExtensionPointName<XmlUndefinedElementFixProvider> EP_NAME = ExtensionPointName.create("com.intellij.xml.undefinedElementFixProvider");

  /**
   *
   * @return null if this provider doesn't know anything about this file; empty array if no fixes are available and no other
   * providers should be asked
   */
  public IntentionAction @Nullable [] createFixes(final @NotNull XmlAttribute attribute) {
    return null;
  }

  /**
   *
   * @return null if this provider doesn't know anything about this file; empty array if no fixes are available and no other
   * providers should be asked
   */
  public LocalQuickFix @Nullable [] createFixes(final @NotNull XmlTag tag) {
    return null;
  }
}
