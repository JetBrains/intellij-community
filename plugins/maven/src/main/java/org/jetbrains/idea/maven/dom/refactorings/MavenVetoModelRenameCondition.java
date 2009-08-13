package org.jetbrains.idea.maven.dom.refactorings;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProperties;

public class MavenVetoModelRenameCondition implements Condition<PsiElement> {
  public boolean value(PsiElement target) {
    if (!MavenDomUtil.isMavenElement(target)) return false;
    XmlTag tag = PsiTreeUtil.getParentOfType(target, XmlTag.class, false);
    if (tag == null) return false;
    return DomUtil.findDomElement(tag, MavenDomProperties.class) == null;
  }
}
