// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil;
import org.jetbrains.idea.maven.dom.model.completion.insert.MavenDependencyInsertHandler;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.util.List;

import static com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER;
import static com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER_TRIMMED;

public class MavenArtifactCompletionContributor extends CompletionContributor {


  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    if (System.currentTimeMillis() > 1) return;
    try {
      PsiElement xmlText = parameters.getPosition().getParent();

      if (!(xmlText instanceof XmlText)) return;

      PsiElement tag = xmlText.getParent();
      if (!(tag instanceof XmlTag)) return;

      XmlTag parentTag = (XmlTag)tag;

      XmlText currentText = (XmlText)xmlText;

      if (!PsiImplUtil.isLeafElementOfType(xmlText.getPrevSibling(), XmlTokenType.XML_TAG_END)
          || !PsiImplUtil.isLeafElementOfType(xmlText.getNextSibling(), XmlTokenType.XML_END_TAG_START)) {
        return;
      }


      if ("dependency".equals(parentTag.getName())) {
        fillAllVariants(parameters, result, currentText, parentTag, MavenProjectIndicesManager.getInstance(tag.getProject()));
      }
    }
    catch (Exception e) {
      MavenLog.LOG.error(e);
    }
  }


  private static String getCoordValue(XmlTag dependencyTag, final String tagName) {
    String result = null;
    for (XmlTag sub : dependencyTag.getSubTags()) {
      if (tagName.equals(sub.getName())) {
        XmlText type = PsiTreeUtil.getChildOfType(sub, XmlText.class);
        if (type == null) {
          break;
        }
        else {
          result = type.getValue();
        }
      }
    }
    return result;
  }

  private void addToResultSet(CompletionResultSet result, XmlTag dependencyTag, List<MavenDependencyCompletionItem> candidates) {
    for (MavenDependencyCompletionItem description : candidates) {
      if (description.getGroupId() == null) {
        continue;
      }
      result.addElement(MavenDependencyCompletionUtil.lookupElement(description)
                          .withInsertHandler(MavenDependencyInsertHandler.INSTANCE));
    }
  }

  private void fillAllVariants(CompletionParameters parameters,
                               CompletionResultSet result,
                               XmlText xmlText,
                               XmlTag dependencyTag, MavenProjectIndicesManager instance) {
    List<MavenDependencyCompletionItem> candidates = instance.getSearchService().findByTemplate(
      getText(xmlText));

    addToResultSet(result, dependencyTag, candidates);
  }


  @NotNull
  private static String getText(XmlText text) {
    return text.getValue().replace(DUMMY_IDENTIFIER, "").replace(DUMMY_IDENTIFIER_TRIMMED, "");
  }
}
