package com.jetbrains.rest.inspections;

import com.intellij.codeInspection.CustomSuppressableInspectionTool;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.psi.PsiElement;
import com.jetbrains.rest.RestBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : catherine
 */
public abstract class RestInspection extends LocalInspectionTool implements CustomSuppressableInspectionTool {
  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return RestBundle.message("INSP.GROUP.rest");
  }

  @NotNull
  @Override
  public String getShortName() {
    return getClass().getSimpleName();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public SuppressIntentionAction[] getSuppressActions(@Nullable PsiElement element) {
    return null;
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element) {
    return false;
  }
}
