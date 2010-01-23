/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInspection.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlFile;
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
  @NonNls private static final String SCRIPT_TAG_NAME = "script";
  private static Set<String> ourTagsWithEmptyEndsNotAllowed = new THashSet<String>(Arrays.asList(SCRIPT_TAG_NAME, "div", "iframe"));

  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override public void visitXmlTag(final XmlTag tag) {
        if (isTagWithEmptyEndNotAllowed(tag)) {
          final ASTNode child = XmlChildRole.EMPTY_TAG_END_FINDER.findChild(tag.getNode());

          if (child != null) {

            final LocalQuickFix fix = new LocalQuickFix() {
              @NotNull
              public String getName() {
                return XmlBundle.message("html.inspections.check.empty.script.tag.fix.message");
              }

              public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
                final XmlTag tag = (XmlTag)descriptor.getPsiElement();
                if (tag == null) return;
                final PsiFile psiFile = tag.getContainingFile();

                if (psiFile == null) return;
                ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(psiFile.getVirtualFile());

                final StringBuilder builder = new StringBuilder(tag.getText());
                builder.replace(builder.length() - 2, builder.length(), "></" + tag.getLocalName() + ">");

                try {
                  final FileType fileType = psiFile.getFileType();
                  PsiFile file = PsiFileFactory.getInstance(tag.getProject()).createFileFromText(
                    "dummy." + (fileType == StdFileTypes.JSP || tag.getContainingFile().getLanguage() == HTMLLanguage.INSTANCE ? "html" : "xml"), builder.toString());

                  tag.replace(((XmlFile)file).getDocument().getRootTag());
                }
                catch (IncorrectOperationException e) {
                  LOG.error(e);
                }
              }

              //to appear in "Apply Fix" statement when multiple Quick Fixes exist
              @NotNull
              public String getFamilyName() {
                return getName();
              }
            };

            holder.registerProblem(tag,
                                   XmlBundle.message("html.inspections.check.empty.script.message"),
                                   tag.getContainingFile().getContext() != null ? 
                                     ProblemHighlightType.INFORMATION:
                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING, 
                                   fix);
          }
        }
      }
    };
  }

  static boolean isTagWithEmptyEndNotAllowed(final XmlTag tag) {
    String tagName = tag.getName();
    if (tag instanceof HtmlTag) tagName = tagName.toLowerCase();

    Language language = tag.getLanguage();
    return (ourTagsWithEmptyEndsNotAllowed.contains(tagName) &&
           language != XMLLanguage.INSTANCE) ||
           (language == HTMLLanguage.INSTANCE &&
            ( !HtmlUtil.isSingleHtmlTagL(tagName) && tagName.indexOf(':') == -1))
      ;
  }

  @NotNull
  public String getGroupDisplayName() {
    return XmlInspectionGroupNames.HTML_INSPECTIONS;
  }

  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("html.inspections.check.empty.tag");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "CheckEmptyScriptTag";
  }
}
