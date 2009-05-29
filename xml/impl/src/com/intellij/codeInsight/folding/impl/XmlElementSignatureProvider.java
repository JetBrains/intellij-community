package com.intellij.codeInsight.folding.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.HtmlUtil;

import java.util.StringTokenizer;

/**
 * @author yole
 */
public class XmlElementSignatureProvider extends ElementSignatureProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.folding.impl.XmlElementSignatureProvider");

  public String getSignature(PsiElement element) {
    if (element instanceof XmlTag) {
      XmlTag tag = (XmlTag)element;
      PsiElement parent = tag.getParent();

      StringBuilder buffer = new StringBuilder();
      buffer.append("tag#");
      String name = tag.getName();
      buffer.append(name.length() == 0 ? "<unnamed>" : name);

      buffer.append("#");
      buffer.append(getChildIndex(tag, parent, name, XmlTag.class));

      if (parent instanceof XmlTag) {
        String parentSignature = getSignature(parent);
        buffer.append(";");
        buffer.append(parentSignature);
      }

      return buffer.toString();
    }
    return null;
  }

  @Override
  protected PsiElement restoreBySignatureTokens(PsiFile file, PsiElement parent, String type, StringTokenizer tokenizer) {
    if (type.equals("tag")) {
      String name = tokenizer.nextToken();

      if (parent instanceof XmlFile) {
        parent = ((XmlFile)parent).getDocument();
      }

      try {
        int index = Integer.parseInt(tokenizer.nextToken());
        PsiElement result = restoreElementInternal(parent, name, index, XmlTag.class);

        if (result == null &&
            file.getFileType() == StdFileTypes.JSP) {
          //TODO: FoldingBuilder API, psi roots, etc?
          if (parent instanceof XmlDocument) {
            // html tag, not found in jsp tree
            result = restoreElementInternal(HtmlUtil.getRealXmlDocument((XmlDocument)parent), name, index, XmlTag.class);
          }
          else if (name.equals("<unnamed>") && parent != null) {
            // scriplet/declaration missed because null name
            result = restoreElementInternal(parent, "", index, XmlTag.class);
          }
        }

        return result;
      }
      catch (NumberFormatException e) {
        LOG.error(e);
        return null;
      }
    }
    return null;
  }
}
