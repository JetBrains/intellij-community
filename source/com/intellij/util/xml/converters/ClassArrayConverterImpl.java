package com.intellij.util.xml.converters;

import com.intellij.util.xml.converters.values.ClassArrayConverter;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.ConvertContext;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Sergey.Vasiliev
 */
public class ClassArrayConverterImpl extends ClassArrayConverter {

   protected final JavaClassReferenceProvider myReferenceProvider;

  public ClassArrayConverterImpl(final Project project) {
    myReferenceProvider =new JavaClassReferenceProvider(project);
    myReferenceProvider.setSoft(true);
    myReferenceProvider.setAllowEmpty(true);
  }

  @NotNull
  public PsiReference[] createReferences(final GenericDomValue genericDomValue, final PsiElement element, final ConvertContext context) {
    final String s = genericDomValue.getStringValue();
    if (s != null) {
      final int offset = ElementManipulators.getOffsetInElement(element);
      final ArrayList<PsiReference> list = new ArrayList<PsiReference>();
      int pos = -1;
      while (true) {
        while (pos + 1 < s.length()) {
          if (!Character.isWhitespace(s.charAt(pos + 1))) {
            break;
          }
          pos++;
        }
        int nextPos = s.indexOf(',', pos + 1);
        if (nextPos == -1) {
          createReference(element, s.substring(pos + 1), pos + 1 + offset, list);
          break;
        }
        else {
          createReference(element, s.substring(pos + 1, nextPos), pos + 1 + offset, list);
          pos = nextPos;
        }
      }
      return list.toArray(new PsiReference[list.size()]);
    }
    return PsiReference.EMPTY_ARRAY;
  }

  private void createReference(final PsiElement element, final String s, final int offset, List<PsiReference> list) {
    final PsiReference[] references = myReferenceProvider.getReferencesByString(s, element, offset);
    //noinspection ManualArrayToCollectionCopy
    for (PsiReference ref: references) {
      list.add(ref);
    }
  }
}
