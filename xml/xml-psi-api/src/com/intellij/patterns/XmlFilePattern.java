// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public class XmlFilePattern<Self extends XmlFilePattern<Self>> extends PsiFilePattern<XmlFile, Self>{

  public XmlFilePattern() {
    super(XmlFile.class);
  }

  protected XmlFilePattern(final @NotNull InitialPatternCondition<XmlFile> condition) {
    super(condition);
  }

  public Self withRootTag(final ElementPattern<XmlTag> rootTag) {
    return with(new PatternCondition<>("withRootTag") {
      @Override
      public boolean accepts(final @NotNull XmlFile xmlFile, final ProcessingContext context) {
        XmlDocument document = xmlFile.getDocument();
        return document != null && rootTag.accepts(document.getRootTag(), context);
      }
    });
  }

  public static class Capture extends XmlFilePattern<Capture> {
  }
}
