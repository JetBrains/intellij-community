package de.plushnikov.intellij.plugin.provider;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.PsiElement;
import de.plushnikov.intellij.lombok.UserMapKeys;

/**
 * Provides implicit usages of lombok fields
 */
public class LombokImplicitUsageProvider implements ImplicitUsageProvider {

  @Override
  public boolean isImplicitUsage(PsiElement element) {
    final Boolean userData = element.getUserData(UserMapKeys.USAGE_KEY);
    return null != userData && userData;
  }

  @Override
  public boolean isImplicitRead(PsiElement element) {
    final Boolean userData = element.getUserData(UserMapKeys.READ_KEY);
    return null != userData && userData;
  }

  @Override
  public boolean isImplicitWrite(PsiElement element) {
    final Boolean userData = element.getUserData(UserMapKeys.WRITE_KEY);
    return null != userData && userData;
  }
}
