package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlFile;

public class MavenUrlConverter extends MavenReferenceConverter<String> {
  protected PsiReference createReference(PsiElement element,
                                         String originalText,
                                         String resolvedText,
                                         TextRange range,
                                         VirtualFile virtualFile,
                                         XmlFile psiFile) {
    return new MavenUrlReference(element, originalText, resolvedText, range);
  }
}