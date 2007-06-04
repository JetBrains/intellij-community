package com.theoryinpractice.testng.intention;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.util.IncorrectOperationException;
import com.theoryinpractice.testng.util.Intentions;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Hani Suleiman Date: Aug 3, 2005 Time: 4:18:11 PM
 */
public class ConvertJavadocIntention extends AbstractProjectIntention {
  @NonNls private static final String TESTNG_PREFIX = "testng.";

  @NotNull
  public String getText() {
    return "Convert TestNG Javadoc to 1.5 annotations";
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!file.isWritable()) return false;
    if (!(file instanceof PsiJavaFile)) return false;
    LanguageLevel languageLevel = ((PsiJavaFile)file).getLanguageLevel();
    if (languageLevel == LanguageLevel.JDK_1_3 || languageLevel == LanguageLevel.JDK_1_4) return false;
    JavadocVisitor visitor = new JavadocVisitor();
    file.accept(visitor);
    return visitor.hasAnnotations;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiJavaFile javaFile = (PsiJavaFile)file;
    for (PsiClass psiClass : javaFile.getClasses()) {
      for (PsiMethod method : psiClass.getMethods()) {
        PsiElementFactory factory = method.getManager().getElementFactory();
        final PsiDocComment docComment = method.getDocComment();
        if (docComment == null) continue;
        nextTag: for (PsiDocTag tag : docComment.getTags()) {
          if (tag.getName().startsWith(TESTNG_PREFIX)) {
            Intentions.checkTestNGInClasspath(psiClass);
            @NonNls String annotationName = StringUtil.capitalize(tag.getName().substring(TESTNG_PREFIX.length()));
            int dash = annotationName.indexOf('-');
            if (dash > -1) {
              annotationName = annotationName.substring(0, dash) + Character.toUpperCase(annotationName.charAt(dash + 1)) + annotationName.substring(dash + 2);
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

            final PsiElement inserted = method.getModifierList().addBefore(factory.createAnnotationFromText(annotationText.toString(), method),
                                                                           method.getModifierList().getFirstChild());
            CodeStyleManager.getInstance(project).shortenClassReferences(inserted);

            //cleanup
            tag.delete();
            for (PsiElement element : docComment.getChildren()) {
              //if it's anything other than a doc token, then it must stay
              if (element instanceof PsiWhiteSpace) continue;
              if (!(element instanceof PsiDocToken)) continue nextTag;
              PsiDocToken docToken = (PsiDocToken)element;
              if (docToken.getTokenType() == PsiDocToken.DOC_COMMENT_DATA && docToken.getText().trim().length() > 0) {
                continue nextTag;
              }
            }
            //at this point, our doc don't have non-empty comments, nor any tags, so we can delete it.
            docComment.delete();
          }
        }
      }
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

  private static class JavadocVisitor extends PsiRecursiveElementVisitor {
    boolean hasAnnotations;

    @Override
    public void visitDocTag(PsiDocTag tag) {
      if (hasAnnotations) return;
      super.visitDocTag(tag);
      if (tag.getName().startsWith(TESTNG_PREFIX)) hasAnnotations = true;
    }
  }
}
