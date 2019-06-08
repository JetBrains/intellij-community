// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion;

import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.onlinecompletion.MavenScopeTable;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;

import java.util.List;

import static com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER;
import static com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER_TRIMMED;
import static com.intellij.patterns.StandardPatterns.string;

/**
 * @author Sergey Evdokimov
 */
public class MavenDependenciesCompletionProvider extends CompletionContributor {

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiElement xmlText = parameters.getPosition().getParent();

    if (!(xmlText instanceof XmlText)) return;

    PsiElement eDependencyTag = xmlText.getParent();
    if (!(eDependencyTag instanceof XmlTag)) return;

    XmlTag dependencyTag = (XmlTag)eDependencyTag;

    if (!"dependency".equals(dependencyTag.getName())) return;

    if (!PsiImplUtil.isLeafElementOfType(xmlText.getPrevSibling(), XmlTokenType.XML_TAG_END)
        || !PsiImplUtil.isLeafElementOfType(xmlText.getNextSibling(), XmlTokenType.XML_END_TAG_START)) {
      return;
    }

    Project project = dependencyTag.getProject();

    DomElement domElement = DomManager.getDomManager(project).getDomElement(dependencyTag);
    if (!(domElement instanceof MavenDomDependency)) {
      return;
    }


    List<MavenDependencyCompletionItem> candidates = MavenProjectIndicesManager.getInstance(project).getSearchService().findByTemplate(
      StringUtil.trim(xmlText.getText().replace(DUMMY_IDENTIFIER, "").replace(DUMMY_IDENTIFIER_TRIMMED, "")));

    for (MavenDependencyCompletionItem candidate : candidates) {
      result.addElement(MavenDependencyCompletionUtil.lookupElement(candidate).withInsertHandler(MavenDependencyInsertHandler.INSTANCE));
    }
    result.restartCompletionOnPrefixChange(string().containsChars(":-."));
  }

  private static class MavenDependencyInsertHandler implements InsertHandler<LookupElement> {

    public static final InsertHandler<LookupElement> INSTANCE = new MavenDependencyInsertHandler();

    @Override
    public void handleInsert(@NotNull final InsertionContext context, @NotNull LookupElement item) {
      Object obj = item.getObject();
      if (!(obj instanceof MavenDependencyCompletionItem)) {
        return;
      }

      MavenDependencyCompletionItem dependencyToSet = (MavenDependencyCompletionItem)obj;

      int startOffset = context.getStartOffset();

      PsiFile psiFile = context.getFile();

      DomFileElement<MavenDomProjectModel> domModel =
        DomManager.getDomManager(context.getProject()).getFileElement((XmlFile)psiFile, MavenDomProjectModel.class);
      if (domModel == null) return;

      boolean shouldInvokeCompletion = false;

      MavenDomDependency managedDependency = findManagedDomDependency(context, dependencyToSet, domModel);
      if (managedDependency == null) {
        String classifier =
          dependencyToSet.getClassifier() == null ? "" : "<classifier>" + dependencyToSet.getClassifier() + "</classifier>\n";
        String packaging =
          dependencyToSet.getPackaging() == null || dependencyToSet.getPackaging().equals("jar")
          ? ""
          : "<packaging>" + dependencyToSet.getPackaging() + "</packaging>\n";
        String usualScope = MavenScopeTable.getUsualScope(dependencyToSet);
        String scope = usualScope == null ? "" : "<scope>" + usualScope + "</scope>\n";
        String version = dependencyToSet.getVersion() == null ? "" : dependencyToSet.getVersion();
        String value = "<groupId>" + dependencyToSet.getGroupId() + "</groupId>\n" +
                       "<artifactId>" + dependencyToSet.getArtifactId() + "</artifactId>\n" +
                       classifier + packaging + scope +
                       "<version>" + version + "</version>";

        context.getDocument().replaceString(startOffset, context.getSelectionEndOffset(), value);

        if (dependencyToSet.getVersion() == null) {
          context.getEditor().getCaretModel().moveToOffset(startOffset + value.length() - "</version>".length());
          shouldInvokeCompletion = true;
        }
      }
      else {
        StringBuilder sb = new StringBuilder();
        sb.append("<groupId>").append(dependencyToSet.getGroupId()).append("</groupId>\n")
          .append("<artifactId>").append(dependencyToSet.getArtifactId()).append("</artifactId>\n");

        String type = managedDependency.getType().getRawText();
        if (type != null && !type.equals("jar")) {
          sb.append("<type>").append(type).append("</type>\n");
        }

        String classifier = managedDependency.getClassifier().getRawText();
        if (StringUtil.isNotEmpty(classifier)) {
          sb.append("<classifier>").append(classifier).append("</classifier>\n");
        }

        context.getDocument().replaceString(startOffset, context.getSelectionEndOffset(), sb);
      }

      context.commitDocument();

      PsiElement e = psiFile.findElementAt(startOffset);
      while (e != null && (!(e instanceof XmlTag) || !"dependency".equals(((XmlTag)e).getName()))) {
        e = e.getParent();
      }

      if (e != null) {
        new ReformatCodeProcessor(psiFile.getProject(), psiFile, e.getTextRange(), false).run();
      }

      if (shouldInvokeCompletion) {
        MavenDependencyCompletionUtil.invokeCompletion(context, CompletionType.BASIC);
      }
    }

    private MavenDomDependency findManagedDomDependency(@NotNull InsertionContext context,
                                                        MavenDependencyCompletionItem dependencyToSet,
                                                        DomFileElement<MavenDomProjectModel> domModel) {
      if (dependencyToSet.getGroupId() == null || dependencyToSet.getArtifactId() == null) {
        return null;
      }
      return MavenDependencyCompletionUtil.findManagedDependency(domModel.getRootElement(),
                                                                 context.getProject(),
                                                                 dependencyToSet.getGroupId(),
                                                                 dependencyToSet.getArtifactId());
    }
  }
}
