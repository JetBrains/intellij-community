/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.xml.util;

import com.intellij.codeInspection.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim Mossienko
 */
public class CheckEmptyScriptTagInspection extends XmlSuppressableInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.util.CheckEmptyScriptTagInspection");
  @NonNls private static final String SCRIPT_TAG_NAME = "script";

  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override public void visitXmlTag(final XmlTag tag) {
        if (isScriptTag(tag)) {
          final ASTNode child = XmlChildRole.EMPTY_TAG_END_FINDER.findChild(tag.getNode());

          if (child != null) {

            final LocalQuickFix fix = new LocalQuickFix() {
              @NotNull
              public String getName() {
                return XmlBundle.message("html.inspections.check.empty.script.tag.fix.message");
              }

              public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
                final StringBuilder builder = new StringBuilder(tag.getText());
                builder.replace(builder.length() - 2, builder.length(), "></" + SCRIPT_TAG_NAME + ">");

                try {
                  final FileType fileType = tag.getContainingFile().getFileType();
                  PsiFile file = PsiFileFactory.getInstance(tag.getProject()).createFileFromText(
                    "dummy." + (fileType == StdFileTypes.JSP || tag.getContainingFile().getLanguage() == StdLanguages.HTML ? "html" : "xml"), builder.toString());

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
                                   fix);
          }
        }
      }
    };
  }

  static boolean isScriptTag(final XmlTag tag) {
    return ( SCRIPT_TAG_NAME.equals(tag.getName()) ||
          (tag instanceof HtmlTag && SCRIPT_TAG_NAME.equalsIgnoreCase(tag.getLocalName()))
        ) && tag.getLanguage() != StdLanguages.XML;
  }

  @NotNull
  public String getGroupDisplayName() {
    return XmlInspectionGroupNames.HTML_INSPECTIONS;
  }

  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("html.inspections.check.empty.script.tag");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "CheckEmptyScriptTag";
  }
}
