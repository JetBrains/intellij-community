package org.jetbrains.yaml;

import com.intellij.lang.HelpID;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.*;

/**
 * @author shalupov
 */
public class YAMLFindUsagesProvider implements FindUsagesProvider {
  @Nullable
  @Override
  public WordsScanner getWordsScanner() {
    return new YAMLWordsScanner();
  }

  @Override
  public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
    return psiElement instanceof PsiNamedElement || psiElement instanceof YAMLScalar;
  }

  @Nullable
  @Override
  public String getHelpId(@NotNull PsiElement psiElement) {
    return HelpID.FIND_OTHER_USAGES;
  }

  @NotNull
  @Override
  public String getType(@NotNull PsiElement element) {
    if (element instanceof YAMLScalarText) {
      return YAMLBundle.message("find.usages.scalar");
    } else if (element instanceof YAMLSequence) {
      return YAMLBundle.message("find.usages.sequence");
    } else if (element instanceof YAMLMapping) {
      return YAMLBundle.message("find.usages.mapping");
    } else if (element instanceof YAMLKeyValue) {
      return YAMLBundle.message("find.usages.key.value");
    } else {
      return "";
    }
  }

  @NotNull
  @Override
  public String getDescriptiveName(@NotNull PsiElement element) {
    if (element instanceof PsiNamedElement) {
      return StringUtil.notNullize(((PsiNamedElement)element).getName(), YAMLBundle.message("find.usages.unnamed"));
    }

    if (element instanceof YAMLScalar) {
      return ((YAMLScalar)element).getTextValue();
    }

    return YAMLBundle.message("find.usages.unnamed");
  }

  @NotNull
  @Override
  public String getNodeText(@NotNull PsiElement element, boolean useFullName) {
    return getDescriptiveName(element);
  }
}