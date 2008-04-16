package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.CustomReferenceConverter;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MavenModuleConverter extends Converter<PsiFile> implements CustomReferenceConverter<PsiFile> {
  public PsiFile fromString(@Nullable @NonNls String s, ConvertContext context) {
    // let me know if it called
    throw new UnsupportedOperationException();
  }

  public String toString(@Nullable PsiFile psiFile, ConvertContext context) {
    // let me know if it called
    throw new UnsupportedOperationException();
  }

  @NotNull
  public PsiReference[] createReferences(GenericDomValue value, PsiElement element, ConvertContext context) {
    VirtualFile virtualFile = context.getInvocationElement().getRoot().getOriginalFile().getVirtualFile();
    XmlFile psiFile = context.getFile();
    String text = value.getStringValue();
    TextRange range = ElementManipulators.getValueTextRange(element);
    return new PsiReference[] {new MavenModuleReference(element, virtualFile, psiFile, text, range)};
  }
}
