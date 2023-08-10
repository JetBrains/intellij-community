package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.InferredAnnotationProvider;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LombokInferredAnnotationProvider implements InferredAnnotationProvider {
  public static final Key<PsiAnnotation> CONTRACT_ANNOTATION = Key.create("lombok.contract");

  @Override
  public @Nullable PsiAnnotation findInferredAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    if (!annotationFQN.equals(JavaMethodContractUtil.ORG_JETBRAINS_ANNOTATIONS_CONTRACT)) return null;
    if (!(listOwner instanceof LombokLightMethodBuilder)) return null;
    return listOwner.getUserData(CONTRACT_ANNOTATION);
  }

  @Override
  public @NotNull List<PsiAnnotation> findInferredAnnotations(@NotNull PsiModifierListOwner listOwner) {
    return ContainerUtil.createMaybeSingletonList(
      findInferredAnnotation(listOwner, JavaMethodContractUtil.ORG_JETBRAINS_ANNOTATIONS_CONTRACT));
  }
}
