// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.generate;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiFile;
import com.intellij.util.Function;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.actions.generate.AbstractDomGenerateProvider;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

public class MavenGenerateDomElementProvider extends AbstractDomGenerateProvider {
  private final Function<? super MavenDomProjectModel, ? extends DomElement> myParentFunction;

  public MavenGenerateDomElementProvider(final @NlsContexts.DetailedDescription String description,
                                         final Class<? extends DomElement> childElementClass,
                                         String mappingId,
                                         Function<? super MavenDomProjectModel, ? extends DomElement> parentFunction) {
    super(description, childElementClass, mappingId);
    myParentFunction = parentFunction;
  }

  @Override
  protected DomElement getParentDomElement(final Project project, final Editor editor, final PsiFile file) {
    MavenDomProjectModel domProjectModel = MavenDomUtil.getMavenDomModel(file, MavenDomProjectModel.class);

    return domProjectModel == null ? null : myParentFunction.fun(domProjectModel);
  }
}