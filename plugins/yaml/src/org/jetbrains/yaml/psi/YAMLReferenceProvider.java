package org.jetbrains.yaml.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author oleg
 */
public class YAMLReferenceProvider extends PsiReferenceProvider {
  public static ExtensionPointName<PsiReferenceProvider> EP = ExtensionPointName.create("com.intellij.yamlReferenceProvider");

  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    final List<PsiReference> references = new ArrayList<PsiReference>();
    for (PsiReferenceProvider provider : EP.getExtensions()) {
      for (PsiReference reference : provider.getReferencesByElement(element, context)) {
        references.add(reference);
      }
    }
    return references.toArray(new PsiReference[references.size()]);
  }
}
