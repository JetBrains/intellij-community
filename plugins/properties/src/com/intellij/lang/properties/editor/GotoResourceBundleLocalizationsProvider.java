/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
      final ArrayList<PropertiesFile> propertiesFilesWithoutCurrent = ContainerUtil.newArrayList(bundlePropertiesFiles);
      propertiesFilesWithoutCurrent.remove(psiFile);
      return ContainerUtil.map(propertiesFilesWithoutCurrent, propertiesFile -> new GotoRelatedItem((PsiElement) propertiesFile, "Other Localizations"));
    } else {
      return Collections.emptyList();
    }
  }
}
