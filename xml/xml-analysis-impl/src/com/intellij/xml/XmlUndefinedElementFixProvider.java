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
  @Nullable
  public IntentionAction[] createFixes(@NotNull final XmlAttribute attribute) {
    return null;
  }

  /**
   *
   * @return null if this provider doesn't know anything about this file; empty array if no fixes are available and no other
   * providers should be asked
   */
  @Nullable
  public LocalQuickFix[] createFixes(@NotNull final XmlTag tag) {
    return null;
  }
}
