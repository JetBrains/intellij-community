package de.plushnikov.intellij.plugin.intention;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNamedElement;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHelper;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AddRequiredLombokBuilderMethodsAction extends AbstractAddLombokBuilderMethodsAction {
  @Override
  public @NotNull String getFamilyName() {
    return LombokBundle.message("add.required.builder.methods.lombok.intention");
  }

  @Override
  List<String> getBuilderMethodNames(@NotNull Pair<PsiAnnotation, PsiNamedElement> elementPair) {
    return BuilderHelper.getAllBuilderMethodNames((PsiModifierListOwner)elementPair.getSecond(), elementPair.getFirst(),
                                                  bi -> NullableNotNullManager.isNotNull(bi.getVariable()));
  }
}
