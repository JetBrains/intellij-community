// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.doc;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.intellij.markdown.utils.MarkdownToHtmlConverterKt.convertMarkdownToHtml;

/**
 * @author Dennis.Ushakov
 */
public class TaskDocumentationProvider extends AbstractDocumentationProvider implements ExternalDocumentationProvider  {

  @Override
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    if (element instanceof TaskPsiElement) {
      final String url = ((TaskPsiElement)element).getTask().getIssueUrl();
      if (url != null) {
        return Collections.singletonList(url);
      }
    }
    return null;
  }

  @Override
  public @Nls String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    if (!(element instanceof TaskPsiElement)) return null;
    final Task task = ((TaskPsiElement)element).getTask();
    final StringBuilder builder = new StringBuilder();
    builder.append("<b>Summary:</b> ").append(task.getSummary()).append("<br>");
    builder.append("<b>Id:</b> ").append(task.getPresentableId()).append("<br>");
    if (task.getCreated() != null) {
      builder.append("<b>Created at:</b> ").append(task.getCreated()).append("<br>");
    }
    if (task.getUpdated() != null) {
      builder.append("<b>Updated at:</b> ").append(task.getUpdated()).append("<br>");
    }
    final String description = task.getDescription();
    if (description != null) {
      builder.append("<b>Description:</b><br>").append(convertMarkdownToHtml(description));
    }
    for (Comment comment : task.getComments()) {
      comment.appendTo(builder);
    }
    return XmlStringUtil.wrapInHtml(builder);
  }

  @Override
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    return object instanceof Task ? new TaskPsiElement(psiManager, (Task)object): null;
  }


  @Override
  public @Nls String fetchExternalDocumentation(Project project, PsiElement element, List<String> docUrls, boolean onHover) {
    return null;
  }

  @Override
  public boolean hasDocumentationFor(PsiElement element, PsiElement originalElement) {
    return element instanceof TaskPsiElement;
  }

  @Override
  public boolean canPromptToConfigureDocumentation(PsiElement element) {
    return false;
  }

  @Override
  public void promptToConfigureDocumentation(PsiElement element) {}
}
