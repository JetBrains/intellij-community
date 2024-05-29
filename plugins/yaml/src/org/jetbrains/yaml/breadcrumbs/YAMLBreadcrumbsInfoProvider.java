// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.breadcrumbs;

import com.intellij.lang.Language;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.*;

import javax.swing.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.List;

public class YAMLBreadcrumbsInfoProvider implements BreadcrumbsProvider {
  private static final Language[] LANGUAGES = new Language[]{YAMLLanguage.INSTANCE};
  
  private static final int SCALAR_MAX_LENGTH = 20;

  @Override
  public Language[] getLanguages() {
    return LANGUAGES;
  }

  @Override
  public boolean acceptElement(@NotNull PsiElement e) {
    return e instanceof YAMLScalar || e instanceof YAMLKeyValue || e instanceof YAMLSequenceItem || e instanceof YAMLDocument;
  }

  @Override
  public boolean acceptStickyElement(@NotNull PsiElement e) {
    // exclude root element IDEA-344788
    return acceptElement(e) && !(e instanceof YAMLDocument);
  }

  @Override
  public @NotNull String getElementInfo(@NotNull PsiElement e) {
    if (e instanceof YAMLDocument) {
      final YAMLFile file = (YAMLFile)e.getContainingFile();
      if (file == null) {
        return "Document";
      }
      final List<YAMLDocument> documents = file.getDocuments();
      return "Document " + getIndexOf(documents, e);
    }
    if (e instanceof YAMLKeyValue) {
      return ((YAMLKeyValue)e).getKeyText() + ':';
    }
    if (e instanceof YAMLSequenceItem) {
      final PsiElement parent = e.getParent();
      if (!(parent instanceof YAMLSequence)) {
        return "Item";
      }
      final List<YAMLSequenceItem> items = ((YAMLSequence)parent).getItems();
      return "Item " + getIndexOf(items, e);
    }
    if (e instanceof YAMLScalar) {
      return StringUtil.first(((YAMLScalar)e).getTextValue(), SCALAR_MAX_LENGTH, true);
    }
    throw new IllegalArgumentException("This element should not pass #acceptElement");
  }

  @Override
  public @Nullable String getElementTooltip(@NotNull PsiElement e) {
    return null;
  }
  
  @Override
  public @NotNull List<? extends Action> getContextActions(@NotNull PsiElement element) {
    if (!(element instanceof YAMLKeyValue || element instanceof YAMLSequenceItem)) {
      return Collections.emptyList();
    }
    String configName = YAMLUtil.getConfigFullName((YAMLPsiElement)element);
    if (configName.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.singletonList(new AbstractAction(YAMLBundle.message("YAMLBreadcrumbsInfoProvider.copy.key.to.clipboard")) {
      @Override
      public void actionPerformed(ActionEvent event) {
        CopyPasteManager.getInstance().setContents(new StringSelection(configName));
      }
    });
  }

  @Override
  public boolean isShownByDefault() {
    return false;
  }

  private static @NotNull String getIndexOf(@NotNull List<?> list, Object o) {
    return String.valueOf(1 + list.indexOf(o)) + '/' + list.size();
  }
}
