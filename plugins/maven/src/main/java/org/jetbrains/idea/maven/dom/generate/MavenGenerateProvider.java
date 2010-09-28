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
package org.jetbrains.idea.maven.dom.generate;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.actions.generate.AbstractDomGenerateProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

public abstract class MavenGenerateProvider<ELEMENT_TYPE extends DomElement> extends AbstractDomGenerateProvider<ELEMENT_TYPE> {
  public MavenGenerateProvider(String description, Class<ELEMENT_TYPE> clazz) {
    super(description, clazz);
  }

  protected DomElement getParentDomElement(Project project, Editor editor, PsiFile file) {
    DomElement el = DomUtil.getContextElement(editor);
    return DomUtil.getFileElement(el).getRootElement();
  }

  @Override
  public ELEMENT_TYPE generate(@Nullable DomElement parent, Editor editor) {
    if (parent == null) return null;
    return doGenerate((MavenDomProjectModel)parent, editor);
  }

  @Nullable
  protected abstract ELEMENT_TYPE doGenerate(@NotNull MavenDomProjectModel mavenModel, Editor editor);

  @Override
  public boolean isAvailableForElement(@NotNull DomElement el) {
    DomElement root = DomUtil.getFileElement(el).getRootElement();
    return root.getModule() != null
           && root instanceof MavenDomProjectModel
           && isAvailableForModel((MavenDomProjectModel)root);
  }

  protected boolean isAvailableForModel(MavenDomProjectModel mavenModel) {
    return true;
  }
}
