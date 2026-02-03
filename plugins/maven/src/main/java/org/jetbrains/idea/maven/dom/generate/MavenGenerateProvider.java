// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.generate;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.actions.generate.AbstractDomGenerateProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

public abstract class MavenGenerateProvider<ELEMENT_TYPE extends DomElement> extends AbstractDomGenerateProvider<ELEMENT_TYPE> {
  public MavenGenerateProvider(@NlsContexts.DetailedDescription String description, Class<ELEMENT_TYPE> clazz) {
    super(description, clazz);
  }

  @Override
  protected DomElement getParentDomElement(Project project, Editor editor, PsiFile file) {
    DomElement el = DomUtil.getContextElement(editor);
    return DomUtil.getFileElement(el).getRootElement();
  }

  @Override
  public ELEMENT_TYPE generate(@Nullable DomElement parent, Editor editor) {
    if (parent == null) return null;
    return doGenerate((MavenDomProjectModel)parent, editor);
  }

  protected abstract @Nullable ELEMENT_TYPE doGenerate(@NotNull MavenDomProjectModel mavenModel, Editor editor);

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
