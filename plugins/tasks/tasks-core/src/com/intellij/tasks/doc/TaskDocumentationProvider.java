/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.tasks.doc;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;

import java.util.Collections;
import java.util.List;

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
    return Collections.emptyList();
  }

  @Override
  public String generateDoc(PsiElement element, PsiElement originalElement) {
    if (!(element instanceof TaskPsiElement)) return null;
    final Task task = ((TaskPsiElement)element).getTask();
    final StringBuilder builder = new StringBuilder("<html>");
    builder.append("<b>Summary:</b> ").append(task.getSummary()).append("<br>");
    builder.append("<b>Id:</b> ").append(task.getId()).append("<br>");
    if (task.getCreated() != null) {
      builder.append("<b>Created at:</b> ").append(task.getCreated()).append("<br>");
    }
    if (task.getUpdated() != null) {
      builder.append("<b>Updated at:</b> ").append(task.getUpdated()).append("<br>");
    }
    if (task.getDescription() != null) {
      builder.append("<b>Description:</b><br>").append(task.getDescription());
    }
    for (Comment comment : task.getComments()) {
      addComment(builder, comment);
    }
    builder.append("</html>");
    return builder.toString();
  }

  private static void addComment(final StringBuilder builder, final Comment comment) {
    builder.append("<hr>");
    builder.append("<b>Author:</b> ").append(comment.getAuthor()).append("<br>");
    builder.append("<b>Date:</b> ").append(comment.getDate()).append("<br>");
    builder.append(comment.getText()).append("<br>");
  }

  @Override
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    return object instanceof Task ? new TaskPsiElement(psiManager, (Task)object): null;
  }


  @Override
  public String fetchExternalDocumentation(Project project, PsiElement element, List<String> docUrls) {
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
