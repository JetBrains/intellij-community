// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class HtmlGotoRelatedProvider extends GotoRelatedProvider {
  @NotNull
  @Override
  public List<? extends GotoRelatedItem> getItems(@NotNull PsiElement context) {
    final PsiFile file = context.getContainingFile();
    if (file == null || !isAvailable(file)) {
      return Collections.emptyList();
    }

    return getRelatedFiles(file);
  }

  private static boolean isAvailable(@NotNull PsiFile psiFile) {
    for (PsiFile file : psiFile.getViewProvider().getAllFiles()) {
      Language language = file.getLanguage();
      if (language.isKindOf(HTMLLanguage.INSTANCE) || language.isKindOf(XHTMLLanguage.INSTANCE)) {
        return true;
      }
    }
    return false;
  }

  private static List<? extends GotoRelatedItem> getRelatedFiles(@NotNull PsiFile file) {
    List<GotoRelatedItem> items = new ArrayList<>();

    for (PsiFile psiFile : file.getViewProvider().getAllFiles()) {
      if (psiFile instanceof XmlFile) {
        final XmlFile xmlFile = (XmlFile)psiFile;

        for (RelatedToHtmlFilesContributor contributor : RelatedToHtmlFilesContributor.EP_NAME.getExtensionList()) {
          HashSet<PsiFile> resultSet = new HashSet<>();
          contributor.fillRelatedFiles(xmlFile, resultSet);
          for (PsiFile f: resultSet) {
            items.add(new GotoRelatedItem(f, contributor.getGroupName()));
          }
        }
      }
    }
    return items;
  }
}
