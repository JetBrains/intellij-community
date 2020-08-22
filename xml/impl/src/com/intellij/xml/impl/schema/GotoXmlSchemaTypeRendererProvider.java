// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.impl.schema;

import com.intellij.codeInsight.navigation.GotoTargetHandler;
import com.intellij.codeInsight.navigation.GotoTargetRendererProvider;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

public class GotoXmlSchemaTypeRendererProvider implements GotoTargetRendererProvider {
  @Override
  public PsiElementListCellRenderer getRenderer(@NotNull PsiElement element, @NotNull GotoTargetHandler.GotoData gotoData) {
    if (element instanceof XmlTag) {
      if (SchemaDefinitionsSearch.isTypeElement((XmlTag)element)) {
        return new MyRenderer("");
      }  else if (SchemaDefinitionsSearch.isElementWithSomeEmbeddedType((XmlTag)element)) {
        return new MyRenderer("xsd:element: ");
      }
    }
    return null;
  }

  private static final class MyRenderer extends PsiElementListCellRenderer<XmlTag> {
    private final String myPrefix;

    private MyRenderer(String prefix) {
      myPrefix = prefix;
    }

    @Override
    public String getElementText(XmlTag element) {
      final XmlAttribute attr = SchemaDefinitionsSearch.getNameAttr(element);
      return myPrefix + (attr == null || attr.getValue() == null ? element.getName() : attr.getValue());
    }

    @Override
    protected String getContainerText(XmlTag element, String name) {
      final PsiFile file = element.getContainingFile();
      return "(" + file.getName() + ")";
    }

    @Override
    protected int getIconFlags() {
      return 0;
    }
  }
}
