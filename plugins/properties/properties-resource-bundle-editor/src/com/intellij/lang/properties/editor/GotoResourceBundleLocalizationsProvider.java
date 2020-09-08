// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class GotoResourceBundleLocalizationsProvider extends GotoRelatedProvider {

  @NotNull
  @Override
  public List<? extends GotoRelatedItem> getItems(@NotNull final DataContext context) {
    final FileEditor editor = PlatformDataKeys.FILE_EDITOR.getData(context);
    if (editor instanceof ResourceBundleEditor) {
      return Collections.emptyList();
    }
    final PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(context);
    if (!(psiFile instanceof PropertiesFile)) {
      return Collections.emptyList();
    }
    final ResourceBundle resourceBundle = ((PropertiesFile)psiFile).getResourceBundle();
    final List<PropertiesFile> bundlePropertiesFiles = resourceBundle.getPropertiesFiles();
    assert bundlePropertiesFiles.size() != 0;
    if (bundlePropertiesFiles.size() != 1) {
      final ArrayList<PropertiesFile> propertiesFilesWithoutCurrent = new ArrayList<>(bundlePropertiesFiles);
      propertiesFilesWithoutCurrent.remove(psiFile);
      return ContainerUtil.map(propertiesFilesWithoutCurrent, propertiesFile -> new GotoRelatedItem((PsiElement) propertiesFile,
                                                                                                    ResourceBundleEditorBundle.message(
                                                                                                      "goto.other.localizations.group")));
    } else {
      return Collections.emptyList();
    }
  }
}
