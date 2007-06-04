package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Hani Suleiman Date: Aug 3, 2005 Time: 4:18:11 PM
 */
public class ConvertJavadocInspection extends LocalInspectionTool {
  @NonNls private static final String TESTNG_PREFIX = "testng.";
  private static final String DISPLAY_NAME = "Convert TestNG Javadoc to 1.5 annotations";

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
    return "ConvertJavadoc";
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new PsiElementVisitor() {
      public void visitReferenceExpression(final PsiReferenceExpression expression) {
      }

      public void visitDocTag(final PsiDocTag tag) {
        if (tag.getName().startsWith(TESTNG_PREFIX)) {
          holder.registerProblem(tag, DISPLAY_NAME, new ConvertJavadocQuickfix());
        }
      }
    };
  }

  private static class ConvertJavadocQuickfix implements LocalQuickFix {
    private static final Logger LOG = Logger.getInstance("#" + ConvertJavadocQuickfix.class.getName());

    @NotNull
    public String getName() {
      return DISPLAY_NAME;
    }

    @NotNull
    public String getFamilyName() {
      return DISPLAY_NAME;
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiDocTag tag = (PsiDocTag)descriptor.getPsiElement();
      final PsiMethod method = PsiTreeUtil.getParentOfType(tag, PsiMethod.class);
      LOG.assertTrue(method != null);
      TestNGUtil.checkTestNGInClasspath(tag);
      @NonNls String annotationName = StringUtil.capitalize(tag.getName().substring(TESTNG_PREFIX.length()));
      int dash = annotationName.indexOf('-');
      if (dash > -1) {
        annotationName =
          annotationName.substring(0, dash) + Character.toUpperCase(annotationName.charAt(dash + 1)) + annotationName.substring(dash + 2);
      }
      annotationName = "org.testng.annotations." + annotationName;
      final StringBuffer annotationText = new StringBuffer("@");
      annotationText.append(annotationName);
      final PsiClass annotationClass = method.getManager().findClass(annotationName, method.getResolveScope());
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
                value = next.substring(1, next.length()).trim();
              }
            }
            else {
              //check if the value is in the first bit too
              if (equals < text.length() - 1) {
                //we have stuff after equals, great
                value = text.substring(equals + 1, text.length()).trim();
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
        final PsiElement inserted = method.getModifierList().addBefore(
          tag.getManager().getElementFactory().createAnnotationFromText(annotationText.toString(), method),
          method.getModifierList().getFirstChild());
        CodeStyleManager.getInstance(project).shortenClassReferences(inserted);


        final PsiDocComment docComment = PsiTreeUtil.getParentOfType(tag, PsiDocComment.class);
        LOG.assertTrue(docComment != null);
        //cleanup
        tag.delete();
        for (PsiElement element : docComment.getChildren()) {
          //if it's anything other than a doc token, then it must stay
          if (element instanceof PsiWhiteSpace) continue;
          if (!(element instanceof PsiDocToken)) return;
          PsiDocToken docToken = (PsiDocToken)element;
          if (docToken.getTokenType() == PsiDocToken.DOC_COMMENT_DATA && docToken.getText().trim().length() > 0) {
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
  }

}
