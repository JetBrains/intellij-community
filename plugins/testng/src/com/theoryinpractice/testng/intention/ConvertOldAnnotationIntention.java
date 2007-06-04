/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: 11/09/2006
 * Time: 19:50:29
 */
package com.theoryinpractice.testng.intention;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.theoryinpractice.testng.util.Intentions;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Hani Suleiman Date: Aug 3, 2005 Time: 4:17:59 PM
 */
public class ConvertOldAnnotationIntention extends AbstractProjectIntention {

  @NotNull
  public String getText() {
    return "Convert old @Configuration TestNG annotations";
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
      PsiElement parent = annotation.getParent();
      if (parent instanceof PsiModifierList) {
        final PsiModifierList modifierList = (PsiModifierList)parent;
        final PsiAnnotation[] annotations = modifierList.getAnnotations();
        for (PsiAnnotation annotation1 : annotations) {
          final String qualifiedName = annotation1.getQualifiedName();
          if (qualifiedName != null && qualifiedName.equals("org.testng.annotations.Configuration")) {
            convertOldAnnotationAttributeToAnnotation(modifierList, annotation1, "beforeTest", "@org.testng.annotations.BeforeTest");
            convertOldAnnotationAttributeToAnnotation(modifierList, annotation1, "beforeTestClass", "@org.testng.annotations.BeforeTest");
            convertOldAnnotationAttributeToAnnotation(modifierList, annotation1, "beforeTestMethod", "@org.testng.annotations.BeforeMethod");
            convertOldAnnotationAttributeToAnnotation(modifierList, annotation1, "beforeSuite", "@org.testng.annotations.BeforeSuite");
            convertOldAnnotationAttributeToAnnotation(modifierList, annotation1, "beforeGroups", "@org.testng.annotations.BeforeGroups");
            convertOldAnnotationAttributeToAnnotation(modifierList, annotation1, "afterTest", "@org.testng.annotations.AfterTest");
            convertOldAnnotationAttributeToAnnotation(modifierList, annotation1, "afterTestClass", "@org.testng.annotations.AfterTest");
            convertOldAnnotationAttributeToAnnotation(modifierList, annotation1, "afterTestMethod", "@org.testng.annotations.AfterMethod");
            convertOldAnnotationAttributeToAnnotation(modifierList, annotation1, "afterSuite", "@org.testng.annotations.AfterSuite");
            convertOldAnnotationAttributeToAnnotation(modifierList, annotation1, "afterGroups", "@org.testng.annotations.AfterGroups");
            annotation1.delete();
          }
        }
      }
    }
  }

  public boolean startInWriteAction() {
    return true;
  }


  private static void convertOldAnnotationAttributeToAnnotation(PsiModifierList modifierList,
                                                                PsiAnnotation annotation, @NonNls String attribute, @NonNls String newAnnotation) throws IncorrectOperationException {

    PsiAnnotationParameterList list = annotation.getParameterList();
    for (PsiNameValuePair pair : list.getAttributes()) {
      if (attribute.equals(pair.getName())) {
        final StringBuffer newAnnotationBuffer = new StringBuffer();
        newAnnotationBuffer.append(newAnnotation).append('(').append(')');
        final PsiElementFactory factory = annotation.getManager().getElementFactory();
        final PsiAnnotation newPsiAnnotation = factory.createAnnotationFromText(newAnnotationBuffer.toString(), modifierList);
        Intentions.checkTestNGInClasspath(newPsiAnnotation);
        CodeStyleManager.getInstance(annotation.getProject()).shortenClassReferences(modifierList.addAfter(newPsiAnnotation, null));
      }
    }
  }

}