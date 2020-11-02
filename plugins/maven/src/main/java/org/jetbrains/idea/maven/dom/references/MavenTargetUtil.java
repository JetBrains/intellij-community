// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.references;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.idea.maven.dom.MavenDomUtil;

public final class MavenTargetUtil {
  public static PsiElement getRefactorTarget(Editor editor, PsiFile file) {
    PsiElement target = getFindTarget(editor, file, true);
    return target == null || !MavenDomUtil.isMavenProperty(target) ? null : target;
  }

  public static PsiElement getFindTarget(Editor editor, PsiFile file, boolean rename) {
    if (editor == null || file == null) return null;
    if (!rename && !MavenDomUtil.isMavenFile(file)) return null;
    PsiElement target = TargetElementUtil.findTargetElement(editor, TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    if (target instanceof MavenPsiElementWrapper) {
      return ((MavenPsiElementWrapper)target).getWrappee();
    }


    if (target == null || isSchema(target)) {
      target = file.findElementAt(editor.getCaretModel().getOffset());
      if (target == null) return null;
    }

    return PsiTreeUtil.getParentOfType(target, XmlTag.class, false);
  }

  private static boolean isSchema(PsiElement element) {
    return element instanceof XmlTag && XmlUtil.XML_SCHEMA_URI.equals(((XmlTag)element).getNamespace());
  }
}
