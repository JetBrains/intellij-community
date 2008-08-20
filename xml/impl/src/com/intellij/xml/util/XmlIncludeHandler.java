package com.intellij.xml.util;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.impl.XmlAspectChangeSetImpl;
import com.intellij.pom.xml.impl.events.XmlElementChangedImpl;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.impl.source.xml.XmlElementImpl;
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
  private final XmlAspect myXmlAspect;
  private final PomModel myModel;

  public XmlIncludeHandler(XmlAspect xmlAspect, final PomModel model) {
    myXmlAspect = xmlAspect;
    myModel = model;
  }

  public boolean shouldCheckFile(@NotNull final VirtualFile psiFile) {
    return psiFile.getFileType() == StdFileTypes.XML;
  }

  public PsiIncludeManager.IncludeInfo[] findIncludes(final PsiFile psiFile) {
    if (!(psiFile instanceof XmlFile)) return PsiIncludeManager.IncludeInfo.EMPTY_ARRAY;

    if (!canContainIncludeTag(psiFile)) return PsiIncludeManager.IncludeInfo.EMPTY_ARRAY;

    final List<PsiIncludeManager.IncludeInfo> result = new ArrayList<PsiIncludeManager.IncludeInfo>();

    psiFile.accept(new XmlRecursiveElementVisitor() {
      @Override public void visitXmlTag(final XmlTag tag) {

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

  public void includeChanged(final PsiElement includeDirective, final PsiFile targetFile, final PomModelEvent event) {
    final PsiElement parent = includeDirective.getParent();
    assert parent instanceof XmlElementImpl;

    final XmlFile xmlFile = (XmlFile)includeDirective.getContainingFile();

    final XmlElementImpl xmlParent = (XmlElementImpl)parent;
    xmlParent.clearCaches();

    final XmlAspectChangeSetImpl changeSet = event.registerChangeSetIfAbsent(myXmlAspect, new XmlAspectChangeSetImpl(myModel));
    changeSet.addChangedFile(xmlFile);

    changeSet.add(new XmlElementChangedImpl(xmlParent));
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

      if (xmlTag.getLocalName().equals(INCLUDE_TAG_NAME) && xmlTag.getAttributeValue("href") != null) {
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

    final FileReferenceSet referenceSet = FileReferenceSet.createSet(xmlAttributeValue, false, true, false);

    final PsiReference reference = referenceSet.getLastReference();
    if (reference == null) return null;

    final PsiElement target = reference.resolve();

    if (!(target instanceof XmlFile)) return null;
    return (XmlFile)target;
  }
}
