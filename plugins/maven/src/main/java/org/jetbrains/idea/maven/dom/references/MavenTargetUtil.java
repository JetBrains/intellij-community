package org.jetbrains.idea.maven.dom.references;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.idea.maven.dom.MavenDomUtil;

public class MavenTargetUtil {
  public static PsiElement getFindTarget(Editor editor, PsiFile file) {
    if (editor == null || file == null) return null;

    PsiElement target = TargetElementUtil.findTargetElement(editor, TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
    if (target instanceof MavenPsiElementWrapper) {
      return ((MavenPsiElementWrapper)target).getWrappee();
    }

    if (target != null && !(target.getContainingFile() instanceof XmlFile)) return target;

    if (target == null || !(target instanceof XmlTag) || isSchema(target)) {
      target = file.findElementAt(editor.getCaretModel().getOffset());
      if (target == null) return null;
    }

    target = PsiTreeUtil.getParentOfType(target, XmlTag.class, false);
    if (target == null) return null;

    if (!MavenDomUtil.isMavenElement(target)) return null;

    return target;
  }

  private static boolean isSchema(PsiElement element) {
    return element instanceof XmlTag && XmlUtil.XML_SCHEMA_URI.equals(((XmlTag)element).getNamespace());
  }
}
