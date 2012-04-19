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
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.IncorrectOperationException;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Hani Suleiman Date: Aug 3, 2005 Time: 4:17:59 PM
 */
public class ConvertAnnotationInspection extends BaseJavaLocalInspectionTool {
  private static final String DISPLAY_NAME = "Convert TestNG annotations to javadocs";

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override public void visitAnnotation(final PsiAnnotation annotation) {
        final @NonNls String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName != null && qualifiedName.startsWith("org.testng.annotations")) {
          holder.registerProblem(annotation, DISPLAY_NAME, new ConvertAnnotationQuickFix());
        }
      }
    };
  }


  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return TestNGUtil.TESTNG_GROUP_NAME;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return "ConvertAnnotations";
  }

  private static class ConvertAnnotationQuickFix implements LocalQuickFix {
    private static final Logger LOG = Logger.getInstance("#" + ConvertAnnotationQuickFix.class.getName());

    @NotNull
    public String getName() {
      return DISPLAY_NAME;
    }

    @NotNull
    public String getFamilyName() {
      return DISPLAY_NAME;
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiAnnotation annotation = (PsiAnnotation)descriptor.getPsiElement();
      final PsiElement parent = annotation.getParent();
      if (parent instanceof PsiModifierList) {
        try {
          final PsiModifierListOwner element = (PsiModifierListOwner)parent.getParent();
          final PsiElementFactory factory = JavaPsiFacade.getInstance(element.getProject()).getElementFactory();
          PsiDocComment docComment = ((PsiDocCommentOwner)element).getDocComment();
          if (docComment == null) {
            docComment = factory.createDocCommentFromText("/**\n */");
            docComment = (PsiDocComment)element.addBefore(docComment, parent);
          }
          @NonNls StringBuffer text = new StringBuffer(convertAnnotationClassToJavadocElement(annotation.getQualifiedName()));
          PsiAnnotationParameterList list = annotation.getParameterList();
          for (PsiNameValuePair pair : list.getAttributes()) {
            text.append(' ');
            if (pair.getName() != null) {
              text.append(pair.getName());
            }
            else {
              text.append("value");
            }
            text.append(" = \"");

            @NonNls String parameterText = StringUtil.stripQuotesAroundValue(pair.getValue().getText());
            if (parameterText.startsWith("{")) {
              parameterText = parameterText.replaceAll("(\\{\\\"|\\\"\\}|\\\"\\w*\\s*\\,\\s*\\w*\\\")", " ").trim();
            }
            text.append(parameterText);
            text.append('\"');
          }
          docComment.addAfter(factory.createDocTagFromText('@' + text.toString()), docComment.getFirstChild());
          annotation.delete();
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }

    private static String convertAnnotationClassToJavadocElement(@NonNls String annotationFqn) {
      char[] chars = annotationFqn.replace("org.testng.annotations", "testng").toCharArray();

      boolean skippedFirst = false;
      StringBuffer sb = new StringBuffer();
      for (char aChar : chars) {
        if ((aChar >= 'A') && (aChar <= 'Z')) {
          if (skippedFirst) {
            sb.append('-');
          }
          else {
            skippedFirst = true;
          }
        }
        sb.append(String.valueOf(aChar));
      }

      return sb.toString().toLowerCase();
    }
  }
}
