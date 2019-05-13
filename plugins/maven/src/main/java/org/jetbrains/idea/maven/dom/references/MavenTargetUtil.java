/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.dom.references;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.idea.maven.dom.MavenDomUtil;

public class MavenTargetUtil {
  public static PsiElement getRefactorTarget(Editor editor, PsiFile file) {
    PsiElement target = getFindTarget(editor, file);
    return target == null || !MavenDomUtil.isMavenProperty(target) ? null : target;
  }

  public static PsiElement getFindTarget(Editor editor, PsiFile file) {
    if (editor == null || file == null) return null;

    PsiElement target = TargetElementUtil.findTargetElement(editor, TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    if (target instanceof MavenPsiElementWrapper) {
      return ((MavenPsiElementWrapper)target).getWrappee();
    }

    if (target == null || isSchema(target)) {
      target = file.findElementAt(editor.getCaretModel().getOffset());
      if (target == null) return null;
    }

    if (!MavenDomUtil.isMavenFile(target)) return null;

    return PsiTreeUtil.getParentOfType(target, XmlTag.class, false);
  }

  private static boolean isSchema(PsiElement element) {
    return element instanceof XmlTag && XmlUtil.XML_SCHEMA_URI.equals(((XmlTag)element).getNamespace());
  }
}
