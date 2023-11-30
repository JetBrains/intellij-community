// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml;

import com.intellij.psi.templateLanguages.TemplateLanguageUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class XmlContentDFA {

  public abstract List<XmlElementDescriptor> getPossibleElements();

  public abstract void transition(XmlTag xmlTag);

  public static @Nullable XmlContentDFA getContentDFA(@NotNull XmlTag parentTag) {

    if (TemplateLanguageUtil.isInsideTemplateFile(parentTag)) return null;

    XmlContentDFA contentDFA = XsContentDFA.createContentDFA(parentTag);
    if (contentDFA != null) return contentDFA;
    return XmlContentDFAImpl.createContentDFA(parentTag);
  }
}
