// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.theoryinpractice.testng.TestngBundle;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Hani Suleiman
 */
public class ConvertJavadocInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final @NonNls String TESTNG_PREFIX = "testng.";

  @Override
  public @Nls @NotNull String getGroupDisplayName() {
    return TestNGUtil.TESTNG_GROUP_NAME;
  }

  @Override
  public @NonNls @NotNull String getShortName() {
    return "ConvertJavadoc";
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override public void visitDocTag(final @NotNull PsiDocTag tag) {
        if (tag.getName().startsWith(TESTNG_PREFIX)) {
          holder.registerProblem(tag, TestngBundle.message("inspection.message.testng.javadoc.can.be.converted.to.annotations"), new ConvertJavadocQuickfix());
        }
      }
    };
  }

  private static class ConvertJavadocQuickfix implements LocalQuickFix {
    private static final Logger LOG = Logger.getInstance(ConvertJavadocQuickfix.class);

    @Override
    public @NotNull String getFamilyName() {
      return TestngBundle.message("intention.family.name.convert.testng.javadoc.to.annotations");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      final PsiDocTag tag = (PsiDocTag)previewDescriptor.getPsiElement();
      doFix(project, tag);
      return IntentionPreviewInfo.DIFF;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiDocTag tag = (PsiDocTag)descriptor.getPsiElement();
      if (!TestNGUtil.checkTestNGInClasspath(tag)) return;
      if (!FileModificationService.getInstance().preparePsiElementsForWrite(tag)) return;
      WriteAction.run(() -> doFix(project, tag));
    }

    private static void doFix(@NotNull Project project, PsiDocTag tag) {
      final PsiMember member = PsiTreeUtil.getParentOfType(tag, PsiMember.class);
      LOG.assertTrue(member != null);
      @NonNls String annotationName = getFQAnnotationName(tag);
      final StringBuilder annotationText = new StringBuilder("@");
      annotationText.append(annotationName);
      final PsiClass annotationClass = DumbService.getInstance(project)
                                                  .computeWithAlternativeResolveEnabled(() -> JavaPsiFacade.getInstance(member.getProject())
                                                                                                           .findClass(annotationName, member.getResolveScope()));
      PsiElement[] dataElements = tag.getDataElements();
      if (dataElements.length > 1) {
        annotationText.append('(');
      }
      if (annotationClass != null) {
        for (PsiMethod attribute : annotationClass.getMethods()) {
          boolean stripQuotes = false;
          PsiType returnType = attribute.getReturnType();
          if (returnType instanceof PsiPrimitiveType) {
            stripQuotes = true;
          }
          for (int i = 0; i < dataElements.length; i++) {
            String text = dataElements[i].getText();
            int equals = text.indexOf('=');
            String value;
            final String key = equals == -1 ? text : text.substring(0, equals).trim();
            if (!key.equals(attribute.getName())) continue;
            annotationText.append(key).append(" = ");
            if (equals == -1) {
              //no equals, so we look in the next token
              String next = dataElements[++i].getText().trim();
              //it's an equals by itself
              if (next.length() == 1) {
                value = dataElements[++i].getText().trim();
              }
              else {
                //otherwise, it's foo =bar, so we strip equals
                value = next.substring(1).trim();
              }
            }
            else {
              //check if the value is in the first bit too
              if (equals < text.length() - 1) {
                //we have stuff after equals, great
                value = text.substring(equals + 1).trim();
              }
              else {
                //nothing after equals, so we just get the next element
                value = dataElements[++i].getText().trim();
              }
            }
            if (stripQuotes && value.charAt(0) == '\"') {
              value = value.substring(1, value.length() - 1);
            }
            annotationText.append(value);
          }
        }
      }

      if (dataElements.length > 1) {
        annotationText.append(')');
      }

      try {
        PsiModifierList modifierList = member.getModifierList();
        PsiAnnotation annotation =
          JavaPsiFacade.getElementFactory(tag.getProject()).createAnnotationFromText(annotationText.toString(), member);
        final PsiElement inserted = modifierList.addBefore(annotation, modifierList.getFirstChild());
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(inserted);


        final PsiDocComment docComment = PsiTreeUtil.getParentOfType(tag, PsiDocComment.class);
        LOG.assertTrue(docComment != null);
        //cleanup
        tag.delete();
        for (PsiElement element : docComment.getChildren()) {
          //if it's anything other than a doc token, then it must stay
          if (element instanceof PsiWhiteSpace) continue;
          if (!(element instanceof PsiDocToken docToken)) return;
          if (docToken.getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA && !docToken.getText().trim().isEmpty()) {
            return;
          }
        }
        //at this point, our doc don't have non-empty comments, nor any tags, so we can delete it.
        docComment.delete();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    private static String getFQAnnotationName(PsiDocTag tag) {
      @NonNls String annotationName = StringUtil.capitalize(tag.getName().substring(TESTNG_PREFIX.length()));
      int dash = annotationName.indexOf('-');
      if (dash > -1) {
        annotationName =
          annotationName.substring(0, dash) + Character.toUpperCase(annotationName.charAt(dash + 1)) + annotationName.substring(dash + 2);
      }
      annotationName = "org.testng.annotations." + annotationName;
      return annotationName;
    }
  }

}
