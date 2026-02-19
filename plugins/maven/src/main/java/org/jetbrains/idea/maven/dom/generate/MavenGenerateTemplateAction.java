// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.generate;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.ui.actions.generate.GenerateDomElementAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

public class MavenGenerateTemplateAction extends GenerateDomElementAction {
  public MavenGenerateTemplateAction(final @NotNull @NlsContexts.DetailedDescription String description,
                                     final @NotNull Class<? extends DomElement> childElementClass,
                                     final @Nullable String mappingId,
                                     @NotNull Function<? super MavenDomProjectModel, ? extends DomElement> parentFunction) {
    super(new MavenGenerateDomElementProvider(description, childElementClass, mappingId, parentFunction));

    getTemplatePresentation().setIcon(ElementPresentationManager.getIconForClass(childElementClass));
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    return psiFile instanceof XmlFile && MavenDomUtil.getMavenDomModel(psiFile, MavenDomProjectModel.class) != null;
  }
}