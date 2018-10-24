// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.breadcrumbs;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiManager;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.intellij.ui.breadcrumbs.BreadcrumbsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BreadcrumbsUtilEx {
  @Nullable
  static FileViewProvider findViewProvider(final VirtualFile file, final Project project) {
    if (file == null || file.isDirectory()) return null;
    return PsiManager.getInstance(project).findViewProvider(file);
  }

  @Nullable
  static BreadcrumbsProvider findProvider(@NotNull Editor editor, VirtualFile file) {
    Project project = editor.getProject();
    return project == null ? null : findProvider(editor, findViewProvider(file, project));
  }

  @Nullable
  public static BreadcrumbsProvider findProvider(@NotNull Editor editor, @Nullable FileViewProvider viewProvider) {
    if (viewProvider == null) return null;

    Boolean forceShown = BreadcrumbsForceShownSettings.getForcedShown(editor);
    if (forceShown == null) {
      return findProvider(true, viewProvider);
    }
    return forceShown ? findProvider(false, viewProvider) : null;
  }

  @Nullable
  public static BreadcrumbsProvider findProvider(boolean checkSettings, @NotNull FileViewProvider viewProvider) {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    if (checkSettings && !settings.isBreadcrumbsShown()) return null;

    Language baseLang = viewProvider.getBaseLanguage();
    if (checkSettings && !settings.isBreadcrumbsShownFor(baseLang.getID())) return null;

    BreadcrumbsProvider provider = BreadcrumbsUtil.getInfoProvider(baseLang);
    if (provider == null) {
      for (Language language : viewProvider.getLanguages()) {
        if (!checkSettings || settings.isBreadcrumbsShownFor(language.getID())) {
          provider = BreadcrumbsUtil.getInfoProvider(language);
          if (provider != null) break;
        }
      }
    }
    return provider;
  }
}
