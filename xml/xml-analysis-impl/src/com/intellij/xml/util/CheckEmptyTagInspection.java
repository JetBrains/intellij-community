/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.xml.util;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlBundle;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Set;

/**
 * @author Maxim Mossienko
 */
public class CheckEmptyTagInspection extends XmlSuppressableInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.util.CheckEmptyTagInspection");
  @NonNls private static final Set<String> ourTagsWithEmptyEndsNotAllowed =
    new THashSet<>(Arrays.asList(HtmlUtil.SCRIPT_TAG_NAME, "div", "iframe"));

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override public void visitXmlTag(final XmlTag tag) {
        if (!isTagWithEmptyEndNotAllowed(tag)) {
          return;
        }
        final ASTNode child = XmlChildRole.EMPTY_TAG_END_FINDER.findChild(tag.getNode());

        if (child == null) {
          return;
        }

        final LocalQuickFix fix = new MyLocalQuickFix();

        holder.registerProblem(tag,
                               XmlBundle.message("html.inspections.check.empty.script.message"),
                               tag.getContainingFile().getContext() != null ?
                               ProblemHighlightType.INFORMATION:
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               fix);
      }
    };
  }

  public static boolean isTagWithEmptyEndNotAllowed(final XmlTag tag) {
    String tagName = tag.getName();
    if (tag instanceof HtmlTag) tagName = tagName.toLowerCase();

    Language language = tag.getLanguage();
    return ourTagsWithEmptyEndsNotAllowed.contains(tagName) && language != XMLLanguage.INSTANCE ||
           language.isKindOf(HTMLLanguage.INSTANCE) && !HtmlUtil.isSingleHtmlTagL(tagName) && tagName.indexOf(':') == -1;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return XmlInspectionGroupNames.HTML_INSPECTIONS;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("html.inspections.check.empty.tag");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "CheckEmptyScriptTag";
  }

  private static class MyLocalQuickFix implements LocalQuickFix {
    @Override
    @NotNull
    public String getName() {
      return XmlBundle.message("html.inspections.check.empty.script.tag.fix.message");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final XmlTag tag = (XmlTag)descriptor.getPsiElement();
      if (tag == null) return;
      final PsiFile psiFile = tag.getContainingFile();

      if (psiFile == null) return;
      if (!FileModificationService.getInstance().prepareFileForWrite(psiFile)) return;

      try {
        XmlUtil.expandTag(tag);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    //to appear in "Apply Fix" statement when multiple Quick Fixes exist
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }
  }
}
