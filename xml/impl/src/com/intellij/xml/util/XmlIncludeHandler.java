package com.intellij.xml.util;

import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mike
 */
public class XmlIncludeHandler implements PsiIncludeManager.PsiIncludeHandler {
  @NonNls private static final String INCLUDE_TAG_NAME = "include";

  public boolean shouldCheckFile(final VirtualFile psiFile) {
    return psiFile.getFileType() == StdFileTypes.XML;
  }

  public PsiIncludeManager.IncludeInfo[] findIncludes(final PsiFile psiFile) {
    if (!canContainIncludeTag(psiFile)) return PsiIncludeManager.IncludeInfo.EMPTY_ARRAY;

    final List<PsiIncludeManager.IncludeInfo> result = new ArrayList<PsiIncludeManager.IncludeInfo>();

    psiFile.accept(new PsiRecursiveElementVisitor() {
      public void visitXmlTag(final XmlTag tag) {

        if (isXInclude(tag)) {
          final XmlFile xmlFile = resolveXIncludeFile(tag);
          if (xmlFile != null) {
            result.add(new PsiIncludeManager.IncludeInfo(
              xmlFile,
              tag,
              new String[] {xmlFile.getName()}
            ));
          }
        }
        else {
          super.visitXmlTag(tag);
        }
      }
    });

    return result.toArray(new PsiIncludeManager.IncludeInfo[result.size()]);
  }

  private static boolean canContainIncludeTag(final PsiFile psiFile) {
    final VirtualFile virtualFile = psiFile.getVirtualFile();

    final String text = LoadTextUtil.loadText(virtualFile).toString();

    if (text.indexOf(INCLUDE_TAG_NAME) < 0 || text.indexOf(XmlUtil.XINCLUDE_URI) < 0) return false;
    return true;
  }

  public static boolean isXInclude(PsiElement element) {
    if (element instanceof XmlTag) {
      XmlTag xmlTag = (XmlTag)element;

      if (xmlTag.getParent() instanceof XmlDocument) return false;

      if (xmlTag.getLocalName().equals(INCLUDE_TAG_NAME)) {
        if (xmlTag.getNamespace().equals(XmlUtil.XINCLUDE_URI)) {
          return true;
        }
      }
    }

    return false;
  }

  @Nullable
  public static XmlFile resolveXIncludeFile(XmlTag xincludeTag) {
    final XmlAttribute hrefAttribute = xincludeTag.getAttribute("href", XmlUtil.XINCLUDE_URI);
    if (hrefAttribute == null) return null;

    final XmlAttributeValue xmlAttributeValue = hrefAttribute.getValueElement();
    if (xmlAttributeValue == null) return null;

    final PsiReference reference = xmlAttributeValue.getReference();
    if (reference == null) return null;

    final PsiElement target = reference.resolve();

    if (!(target instanceof XmlFile)) return null;
    return (XmlFile)target;
  }
}
