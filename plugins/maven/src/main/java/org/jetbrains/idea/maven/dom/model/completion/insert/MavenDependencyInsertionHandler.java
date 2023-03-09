// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion.insert;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomArtifactCoordinates;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates;
import org.jetbrains.idea.maven.onlinecompletion.MavenScopeTable;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;

import static org.jetbrains.idea.maven.dom.model.completion.insert.MavenDependencyInsertionTrackerKt.logMavenDependencyInsertion;

public class MavenDependencyInsertionHandler implements InsertHandler<LookupElement> {

  public static final InsertHandler<LookupElement> INSTANCE = new MavenDependencyInsertionHandler();

  @Override
  public void handleInsert(@NotNull final InsertionContext context, @NotNull LookupElement item) {
    /*if (TemplateManager.getInstance(context.getProject()).getActiveTemplate(context.getEditor()) != null) {
      return; // Don't brake the template.
    }*/
    Object object = item.getObject();
    if (!(object instanceof MavenRepositoryArtifactInfo)) {
      return;
    }
    MavenRepositoryArtifactInfo completionItem = (MavenRepositoryArtifactInfo)object;
    PsiFile contextFile = context.getFile();
    if (!(contextFile instanceof XmlFile)) return;
    XmlFile xmlFile = (XmlFile)contextFile;
    PsiElement element = xmlFile.findElementAt(context.getStartOffset());
    XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    if (tag == null) {
      return;
    }
    context.commitDocument();
    MavenDomShortArtifactCoordinates domCoordinates = getDomCoordinatesFromCurrentTag(context, tag);
    if (domCoordinates == null) {
      return;
    }
    setDependency(context, completionItem, (XmlFile)contextFile, domCoordinates);

    logMavenDependencyInsertion(context, item, completionItem);
  }


  private static MavenDomShortArtifactCoordinates getDomCoordinatesFromCurrentTag(@NotNull InsertionContext context, @NotNull XmlTag tag) {
    DomElement element = DomManager.getDomManager(context.getProject()).getDomElement(tag);
    //todo: show notification
    if (element instanceof MavenDomShortArtifactCoordinates) {
      tag.getValue().setText("");
      return (MavenDomShortArtifactCoordinates)element;
    }
    //try parent
    element = DomManager.getDomManager(context.getProject()).getDomElement(tag.getParentTag());
    if (element instanceof MavenDomShortArtifactCoordinates) {
      return (MavenDomShortArtifactCoordinates)element;
    }

    return null;
  }

  protected void setDependency(@NotNull InsertionContext context,
                               MavenRepositoryArtifactInfo completionItem,
                               XmlFile contextFile, MavenDomShortArtifactCoordinates domCoordinates) {
    domCoordinates.getGroupId().setStringValue(completionItem.getGroupId());

    domCoordinates.getArtifactId().setStringValue(completionItem.getArtifactId());

    if (domCoordinates instanceof MavenDomDependency) {
      String scope = MavenScopeTable.getUsualScope(completionItem);
      if (scope != null) {
        ((MavenDomDependency)domCoordinates).getScope().setStringValue(scope);
      }
    }

    DomFileElement<MavenDomProjectModel> domModel =
      DomManager.getDomManager(context.getProject()).getFileElement(contextFile, MavenDomProjectModel.class);


    if (!MavenDependencyCompletionUtil.isInsideManagedDependency(domCoordinates)) {
      MavenDomDependency declarationOfDependency =
        MavenDependencyCompletionUtil.findManagedDependency(domModel.getRootElement(), context.getProject(), completionItem.getGroupId(),
                                                            completionItem.getArtifactId());
      if (declarationOfDependency != null) {
        if (domCoordinates instanceof MavenDomDependency) {
          if (declarationOfDependency.getType().getRawText() != null) {
            ((MavenDomDependency)domCoordinates).getType().setStringValue(declarationOfDependency.getType().getRawText());
          }
          if (declarationOfDependency.getClassifier().getRawText() != null) {
            ((MavenDomDependency)domCoordinates).getClassifier().setStringValue(declarationOfDependency.getClassifier().getRawText());
          }
        }
        return;
      }
    }

    if (domCoordinates instanceof MavenDomArtifactCoordinates) {
      insertVersion(context, completionItem, (MavenDomArtifactCoordinates)domCoordinates);
    }
  }

  private static void insertVersion(@NotNull InsertionContext context,
                                    MavenRepositoryArtifactInfo completionItem,
                                    MavenDomArtifactCoordinates domCoordinates) {
    if (completionItem.getItems().length == 1 && completionItem.getVersion() != null) {
      domCoordinates.getVersion().setStringValue(completionItem.getVersion());
    }
    else {
      domCoordinates.getVersion().setStringValue("");

      int versionPosition = domCoordinates.getVersion().getXmlTag().getValue().getTextRange().getStartOffset();

      context.getEditor().getCaretModel().moveToOffset(versionPosition);

      MavenDependencyCompletionUtil.invokeCompletion(context, CompletionType.BASIC);
    }
  }
}
