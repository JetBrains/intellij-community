package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import de.plushnikov.intellij.plugin.processor.clazz.SetterProcessor;
import de.plushnikov.intellij.plugin.processor.field.SetterFieldProcessor;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DelombokSetterHandler implements CodeInsightActionHandler {

  private final SetterProcessor setterProcessor;
  private final SetterFieldProcessor setterFieldProcessor;

  public DelombokSetterHandler() {
    setterProcessor = new SetterProcessor();
    setterFieldProcessor = new SetterFieldProcessor();
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (StdFileTypes.JAVA.equals(file.getFileType())) {
      final PsiJavaFile javaFile = (PsiJavaFile) file;
      for (PsiClass psiClass : javaFile.getClasses()) {
        processClass(project, psiClass);
      }
    }
  }

  protected void processClass(@NotNull Project project, @NotNull PsiClass psiClass) {
    final PsiAnnotation psiAnnotation = PsiAnnotationUtil.findAnnotation(psiClass, setterProcessor.getSupportedAnnotation());
    if (null != psiAnnotation) {
      List<? super PsiElement> classSetters = setterProcessor.process(psiClass);
      createVanillaSetters(project, psiClass, classSetters);

      psiAnnotation.delete();
    }

    List<? super PsiElement> fieldSetters = setterFieldProcessor.process(psiClass);
    if (!fieldSetters.isEmpty()) {
      createVanillaSetters(project, psiClass, fieldSetters);
    }

    for (PsiField psiField : psiClass.getFields()) {
      final PsiAnnotation psiFieldAnnotation = PsiAnnotationUtil.findAnnotation(psiField, setterFieldProcessor.getSupportedAnnotation());
      if (null != psiFieldAnnotation) {
        psiFieldAnnotation.delete();
      }
    }
  }

  private void createVanillaSetters(Project project, PsiClass psiClass, List<? super PsiElement> classSetters) {
    for (Object psiElement : classSetters) {
      final PsiMethod lombokMethod = (PsiMethod) psiElement;

      String propertyName = PropertyUtil.getPropertyName(lombokMethod);
      if (null != propertyName) {
        PsiField propertyField = PropertyUtil.findPropertyField(project, psiClass, propertyName, lombokMethod.hasModifierProperty(PsiModifier.STATIC));
        if (null != propertyField) {
          PsiMethod propertyGetter = PropertyUtil.generateSetterPrototype(propertyField);
          final String accessModifier = PsiUtil.getAccessModifier(PsiUtil.getAccessLevel(lombokMethod.getModifierList()));
          if (null != accessModifier) {
            PsiUtil.setModifierProperty(propertyGetter, accessModifier, true);
          }
          psiClass.add(propertyGetter);
        }
      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
