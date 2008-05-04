package com.intellij.lang.properties;

import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInspection.i18n.CreatePropertyFix;
import com.intellij.codeInspection.i18n.I18nUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author cdr
 */
public class PropertyReference extends PropertyReferenceBase implements QuickFixProvider {
  @Nullable private final String myBundleName;

  public PropertyReference(@NotNull String key, @NotNull PsiElement element, @Nullable final String bundleName, final boolean soft) {
    super(key, soft, element);
    myBundleName = bundleName;
  }

  @Nullable
  protected List<PropertiesFile> getPropertiesFiles() {
    if (myBundleName == null) {
      return null;
    }
    return I18nUtil.propertiesFilesByBundleName(myBundleName, myElement);
  }

  public void registerQuickfix(HighlightInfo info, PsiReference reference) {
    List<PropertiesFile> propertiesFiles = I18nUtil.propertiesFilesByBundleName(myBundleName, reference.getElement());
    CreatePropertyFix fix = new CreatePropertyFix(myElement, myKey, propertiesFiles);
    QuickFixAction.registerQuickFixAction(info, fix);
  }

}
