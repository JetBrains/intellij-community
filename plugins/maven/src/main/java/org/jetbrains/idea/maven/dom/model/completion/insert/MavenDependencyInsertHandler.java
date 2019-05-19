// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion.insert;

import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.XmlTagImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil;
import org.jetbrains.idea.maven.onlinecompletion.MavenScopeTable;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.utils.MavenLog;

public class MavenDependencyInsertHandler implements InsertHandler<LookupElement> {

  public static final InsertHandler<LookupElement> INSTANCE = new MavenDependencyInsertHandler();


  @Override
  public void handleInsert(@NotNull final InsertionContext context, @NotNull LookupElement item) {
    try {
      doHandleInsert(context, item);
    }
    catch (Throwable e) {
      MavenLog.LOG.error(e);
    }
  }

  private void doHandleInsert(@NotNull final InsertionContext context, @NotNull LookupElement item) {

    MavenDependencyCompletionItem mavenId = extract(item);

    int startOffset = context.getStartOffset();

    PsiFile psiFile = context.getFile();
    XmlTagImpl dependencyTag = getDependencyTag(startOffset, psiFile);
    if (dependencyTag == null) {
      return;
    }

    boolean shouldInvokeCompletion = setDependency(context, mavenId, dependencyTag);

    context.commitDocument();

    PsiElement e = getDependencyTag(startOffset, psiFile);

    if (e != null) {
      new ReformatCodeProcessor(psiFile.getProject(), psiFile, e.getTextRange(), false).run();
    }

    if (shouldInvokeCompletion) {
      MavenDependencyCompletionUtil.invokeCompletion(context, CompletionType.BASIC);
    }
  }

  @Nullable
  private XmlTagImpl getDependencyTag(int startOffset, PsiFile psiFile) {
    PsiElement e = psiFile.findElementAt(startOffset);
    while (e != null && (!(e instanceof XmlTagImpl) || !"dependency".equals(((XmlTagImpl)e).getName()))) {
      e = e.getParent();
    }
    return (XmlTagImpl)e;
  }

  private boolean setDependency(@NotNull InsertionContext context,
                                @NotNull MavenDependencyCompletionItem item, XmlTagImpl dependencyTag) {

    int startOffset = dependencyTag.getStartOffset() + "<dependency>".length();
    int endOffset = dependencyTag.getTextRange().getEndOffset();
    while ("\n".equals(context.getDocument().getText(new TextRange(endOffset - 1, endOffset)))) {
      endOffset--;
    }
    endOffset -= "</dependency>".length();

    if (item.getGroupId() == null) {
      return false;
    }
    else if (item.getArtifactId() == null) {
      String value = "\n<groupId>" + item.getGroupId() + "</groupId>\n" +
                     "<artifactId></artifactId>";

      context.getDocument().replaceString(startOffset, endOffset, value);
      context.getEditor().getCaretModel().moveToOffset(startOffset + value.length() - "</artifactId>".length());
      return true;
    }
    else if (item.getVersion() == null) {
      String value = "\n<groupId>" + item.getGroupId() + "</groupId>\n" +
                     "<artifactId>" + item.getArtifactId() + "</artifactId>\n" +
                     "<version></version>\n";

      context.getDocument().replaceString(startOffset, endOffset, value);
      context.getEditor().getCaretModel().moveToOffset(startOffset + value.length() - "</version>".length());
      return true;
    }
    else {
      String classifier = item.getClassifier() == null ? "" : "<classifier>" + item.getClassifier() + "</classifier>\n";
      String packaging =
        item.getPackaging() == null || item.getPackaging().equals("jar") ? "" : "<packaging>" + item.getPackaging() + "</packaging>\n";
      String usualScope = MavenScopeTable.getUsualScope(item);
      String scope = usualScope == null ? "" : "<scope>" + usualScope + "</scope>\n";
      String value = "\n<groupId>" + item.getGroupId() + "</groupId>\n" +
                     "<artifactId>" + item.getArtifactId() + "</artifactId>\n" +
                     classifier + packaging + scope +
                     "<version>" + item.getVersion() + "</version>\n";

      context.getDocument().replaceString(startOffset, endOffset, value);
      return false;
    }
  }

  private static MavenDependencyCompletionItem extract(LookupElement item) {
    Object object = item.getObject();
    if (object instanceof MavenDependencyCompletionItem) {
      return (MavenDependencyCompletionItem)object;
    }
    else {
      return new MavenDependencyCompletionItem(item.getLookupString(), null);
    }
  }
}
