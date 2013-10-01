/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.properties.projectView;

import com.intellij.ide.DeleteProvider;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author cdr
 */
class ResourceBundleDeleteProvider implements DeleteProvider {
  private static final Function<PropertiesFile,PsiElement> MAPPER = new Function<PropertiesFile, PsiElement>() {
    @Override
    public PsiElement fun(PropertiesFile propertiesFile) {
      return propertiesFile.getContainingFile();
    }
  };
  private final ResourceBundle myResourceBundle;

  public ResourceBundleDeleteProvider(ResourceBundle resourceBundle) {
    myResourceBundle = resourceBundle;
  }

  public void deleteElement(@NotNull DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    List<PropertiesFile> propertiesFiles = myResourceBundle.getPropertiesFiles(project);
    assert project != null;
    new SafeDeleteHandler().invoke(project, ContainerUtil.map2Array(propertiesFiles, PsiElement.class, MAPPER), dataContext);
  }

  public boolean canDeleteElement(@NotNull DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    return project != null;
  }
}
