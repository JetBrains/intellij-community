package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.j2ee.openapi.ex.ExternalResourceManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDoctype;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;

/**
 * @author mike
 */
public class IgnoreExtResourceAction extends BaseIntentionAction {
  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (!(file instanceof XmlFile)) return false;

    int offset = editor.getCaretModel().getOffset();
    String uri = findUri(file, offset);

    if (uri == null) return false;

    final XmlFile xmlFile = XmlUtil.findXmlFile(file, uri);
    if (xmlFile != null) return false;

    if (!uri.startsWith("http://") && !uri.startsWith("ftp://")) return false;

    setText("Ignore External Resource");
    return true;
  }

  public String getFamilyName() {
    return "Ignore External Resource";
  }


  private String findUri(PsiFile file, int offset) {
    final PsiElement currentElement = file.findElementAt(offset);
    PsiElement element = PsiTreeUtil.getParentOfType(currentElement, XmlDoctype.class);
    if (element != null) {
      return ((XmlDoctype)element).getDtdUri();
    }

    final XmlAttribute attribute = PsiTreeUtil.getParentOfType(currentElement, XmlAttribute.class);
    if(attribute != null && attribute.isNamespaceDeclaration()){
      final String uri = attribute.getValue();
      final PsiElement parent = attribute.getParent();
      if(uri != null && parent instanceof XmlTag && ((XmlTag)parent).getNSDescriptor(uri, true) == null){
        return uri;
      }
    }
    return null;
  }

  public void invoke(final Project project, final Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final String uri = findUri(file, offset);
    if (uri == null) return;

    ExternalResourceManagerEx.getInstanceEx().addIgnoredResource(uri);
  }

}
