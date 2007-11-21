package com.intellij.xml.util;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.paths.PathReferenceManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.xml.XmlTagImpl;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mike
 */
public class XmlIncludeHandler implements PsiIncludeManager.PsiIncludeHandler {
  @NonNls private static final String INCLUDE_TAG_NAME = "include";

  public boolean shouldCheckFile(@NotNull final VirtualFile psiFile) {
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
              new String[] {xmlFile.getName()}, XmlIncludeHandler.this));
          }
        }
        else {
          super.visitXmlTag(tag);
        }
      }
    });

    return result.toArray(new PsiIncludeManager.IncludeInfo[result.size()]);
  }

  public void includeChanged(final PsiElement includeDirective, final PsiFile targetFile, final PsiTreeChangeEvent event) {
    assert includeDirective instanceof XmlTagImpl;

    ((XmlTagImpl)includeDirective).clearCaches();
    //todo: fire pom event
  }

  private static boolean canContainIncludeTag(final PsiFile psiFile) {
    final String text = psiFile.getText();

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

    final PsiReference[] references = PathReferenceManager.getInstance().createReferences(xmlAttributeValue, false, true, true);

    final PsiReference reference = references.length > 0 ? references[0] : null;
    if (reference == null) return null;

    final PsiElement target = reference.resolve();

    if (!(target instanceof XmlFile)) return null;
    return (XmlFile)target;
  }
}
