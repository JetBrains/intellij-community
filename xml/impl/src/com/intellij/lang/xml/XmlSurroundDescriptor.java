package com.intellij.lang.xml;

import com.intellij.codeInsight.generation.surroundWith.TemplateSurrounder;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.XmlContextType;
import com.intellij.codeInsight.template.HtmlContextType;
import com.intellij.codeInsight.template.JspContextType;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class XmlSurroundDescriptor implements SurroundDescriptor {
  @NotNull public PsiElement[] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
    final Pair<XmlTagChild, XmlTagChild> childrenInRange = XmlUtil.findTagChildrenInRange(file, startOffset, endOffset);
    if (childrenInRange == null) {
      final PsiElement elementAt = file.findElementAt(startOffset);
      if (elementAt instanceof XmlToken &&
          ((XmlToken)elementAt).getTokenType() == XmlTokenType.XML_DATA_CHARACTERS) {
        return new PsiElement[] {elementAt};
      }
      return PsiElement.EMPTY_ARRAY;
    }
    List<PsiElement> result = new ArrayList<PsiElement>();
    PsiElement first = childrenInRange.getFirst();
    PsiElement last = childrenInRange.getSecond();
    while(true) {
      result.add(first);
      if (first == last) break;
      first = first.getNextSibling();
    }

    return result.toArray(new PsiElement[result.size()]);
  }

  @NotNull public Surrounder[] getSurrounders() {
    List<TemplateSurrounder> surrounders = new ArrayList<TemplateSurrounder>();
    TemplateImpl[] templates = TemplateSettings.getInstance().getTemplates();
    for (TemplateImpl template : templates) {
      if (template.isDeactivated() || !template.isSelectionTemplate()) continue;
      if (isEnabledInXml(template)) {
        surrounders.add(new TemplateSurrounder(template));
      }
    }
    return surrounders.toArray(new Surrounder[surrounders.size()]);
  }

  public static boolean isEnabledInXml(final TemplateImpl template) {
    final TemplateContext context = template.getTemplateContext();
    return new XmlContextType().isEnabled(context) ||
           new HtmlContextType().isEnabled(context) ||
           new JspContextType().isEnabled(context);
  }
}
