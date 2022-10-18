// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion.insert;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.DependencyConflictId;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.generate.GenerateDependencyAction;
import org.jetbrains.idea.maven.dom.generate.GenerateManagedDependencyAction;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.model.completion.MavenCoordinateCompletionContributor;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;
import org.jetbrains.idea.maven.statistics.MavenDependencyInsertionCollector;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.jetbrains.idea.maven.dom.model.completion.insert.MavenDependencyInsertionTrackerKt.logMavenDependencyInsertion;

public class MavenTopLevelDependencyInsertionHandler implements InsertHandler<LookupElement> {

  public static final InsertHandler<LookupElement> INSTANCE = new MavenTopLevelDependencyInsertionHandler();

  @Override
  public void handleInsert(@NotNull final InsertionContext context, @NotNull LookupElement item) {
    if (TemplateManager.getInstance(context.getProject()).getActiveTemplate(context.getEditor()) != null) {
      return; // Don't brake the template.
    }
    Object object = item.getObject();
    if (!(object instanceof MavenRepositoryArtifactInfo)) {
      return;
    }
    MavenRepositoryArtifactInfo completionItem = (MavenRepositoryArtifactInfo)object;
    PsiFile contextFile = context.getFile();
    if (!(contextFile instanceof XmlFile)) return;
    Project project = context.getProject();
    MavenDomProjectModel model = MavenDomUtil.getMavenDomModel(contextFile, MavenDomProjectModel.class);
    if (model == null) {
      return;
    }
    final Map<DependencyConflictId, MavenDomDependency>
      managedDependencies = GenerateManagedDependencyAction.collectManagingDependencies(model);
    PsiElement element = contextFile.findElementAt(context.getStartOffset());
    if (!(element instanceof XmlText)) {
      element = PsiTreeUtil.getParentOfType(element, XmlText.class);
      if (element == null) {
        return;
      }
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    MavenDomDependency dependency =
      GenerateDependencyAction.createDependency(model, context.getEditor(), managedDependencies, Collections.singletonList(completionItem));

    element.delete();
    if (dependency != null && dependency.getXmlTag() != null) {
      context.getEditor().getCaretModel().moveToOffset(dependency.getXmlTag().getTextOffset());
    }
    context.commitDocument();

    logMavenDependencyInsertion(context, item, completionItem);
  }
}
