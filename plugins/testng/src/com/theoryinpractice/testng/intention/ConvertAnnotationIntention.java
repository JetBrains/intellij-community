package com.theoryinpractice.testng.intention;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.IncorrectOperationException;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Hani Suleiman Date: Aug 3, 2005 Time: 4:17:59 PM
 */
public class ConvertAnnotationIntention extends AbstractProjectIntention {

  @NotNull
  public String getText() {
    return "Convert TestNG annotations to javadocs";
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!file.isWritable()) return false;
    if (!(file instanceof PsiJavaFile)) return false;
    AnnotationsVisitor visitor = new AnnotationsVisitor();
    file.accept(visitor);
    return visitor.hasAnnotations;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    for (PsiAnnotation annotation : TestNGUtil.getTestNGAnnotations(file)) {
      final PsiElement parent = annotation.getParent();
      if (parent instanceof PsiModifierList) {
        final PsiModifierListOwner element = (PsiModifierListOwner)parent.getParent();
        final PsiElementFactory factory = element.getManager().getElementFactory();
        PsiDocComment docComment = ((PsiDocCommentOwner)element).getDocComment();
        if (docComment == null) {
          docComment = factory.createDocCommentFromText("/**\n */", element);
          docComment = (PsiDocComment)element.addBefore(docComment, parent);
        }
        final PsiModifierList modifierList = (PsiModifierList)parent;
        final PsiAnnotation[] annotations = modifierList.getAnnotations();
        for (PsiAnnotation annotation1 : annotations) {
          @NonNls StringBuffer text = new StringBuffer(convertAnnotationClassToJavadocElement(annotation1.getQualifiedName()));
          PsiAnnotationParameterList list = annotation1.getParameterList();
          for (PsiNameValuePair pair : list.getAttributes()) {
            text.append(' ');
            if (pair.getName() != null) {
              text.append(pair.getName());
            }
            else {
              text.append("value");
            }
            text.append(" = \"");

            @NonNls String parameterText = pair.getValue().getText();
            if (parameterText.startsWith("{")) {
              parameterText = parameterText.replaceAll("(\\{\\\"|\\\"\\}|\\\"\\w*\\,\\w*\\\")", " ").trim();
            }
            text.append(parameterText);
            text.append('\"');
          }
          docComment.addAfter(factory.createDocTagFromText('@' + text.toString(), element), docComment.getFirstChild());
          annotation1.delete();
        }
      }
    }
    PsiJavaFile javaFile = (PsiJavaFile)file;
    final PsiImportList importList = javaFile.getImportList();
    if (importList != null) {
      PsiImportStatement annotationsImport = importList.findOnDemandImportStatement("org.testng.annotations");
      if (annotationsImport != null) {
        annotationsImport.delete();
      }
    }
  }

  public boolean startInWriteAction() {
    return true;
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
