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

package com.intellij.util.xml.model.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.model.DomModel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class DomModelFactory<T extends DomElement, M extends DomModel<T>, C extends PsiElement> extends BaseDomModelFactory<Module, T, M, C> {

  protected DomModelFactory(@NotNull Class<T> aClass, final Project project, @NonNls String name) {
    super(aClass, project, name);
  }

  @Override
  protected Module getModelScope(final XmlFile file) {

    return ModuleUtil.findModuleForPsiElement(file);
  }

  @NotNull
  public Set<XmlFile> getConfigFiles(@Nullable C context) {
    if (context == null) {
      return Collections.emptySet();
    }
    final M model = getModel(context);
    if (model == null) {
      return Collections.emptySet();
    }
    else {
      return model.getConfigFiles();
    }
  }

  public List<DomFileElement<T>> getFileElements(M model) {
    final ArrayList<DomFileElement<T>> list = new ArrayList<>(model.getConfigFiles().size());
    for (XmlFile configFile : model.getConfigFiles()) {
      final DomFileElement<T> element = DomManager.getDomManager(configFile.getProject()).getFileElement(configFile, myClass);
      if (element != null) {
        list.add(element);
      }
    }
    return list;
  }
}
