package org.jetbrains.idea.maven.dom.refactorings;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import org.jetbrains.idea.maven.dom.MavenDomUtil;

public class MavenVetoModelRenameCondition implements Condition<PsiElement> {
  public boolean value(PsiElement target) {
    return MavenDomUtil.isMavenFile(target) && !MavenDomUtil.isMavenProperty(target);
  }
}
