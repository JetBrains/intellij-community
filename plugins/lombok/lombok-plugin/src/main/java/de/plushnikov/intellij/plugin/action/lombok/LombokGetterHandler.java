package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import de.plushnikov.intellij.plugin.processor.clazz.GetterProcessor;
import de.plushnikov.intellij.plugin.processor.field.GetterFieldProcessor;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class LombokGetterHandler implements CodeInsightActionHandler {

  private final GetterProcessor getterProcessor;
  private final GetterFieldProcessor getterFieldProcessor;

  public LombokGetterHandler() {
    getterProcessor = new GetterProcessor();
    getterFieldProcessor = new GetterFieldProcessor();
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (StdFileTypes.JAVA.equals(file.getFileType())) {
      final PsiJavaFile javaFile = (PsiJavaFile) file;

//      PsiClass psiClass = OverrideImplementUtil.getContextClass(project, editor, file, false);
      for (PsiClass psiClass : javaFile.getClasses()) {
        processClass(project, psiClass, JavaCodeStyleManager.getInstance(project));
      }
    }
  }

  protected void processClass(@NotNull Project project, @NotNull PsiClass psiClass, JavaCodeStyleManager javaCodeStyleManager) {
    Map<String, PsiMethod> psiMethodMap = PropertyUtil.getAllProperties(psiClass, false, true, false);
    for (Map.Entry<String, PsiMethod> methodEntry : psiMethodMap.entrySet()) {
      final String propertyName = methodEntry.getKey();
      final PsiMethod psiMethod = methodEntry.getValue();

      PsiField propertyField = PropertyUtil.findPropertyFieldByMember(psiMethod);
      PsiModifierList modifierList = propertyField.getModifierList();
      if (null != modifierList) {
        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory();
//        PsiAnnotation newAnnotation = elementFactory.createAnnotationFromText("@" + Getter.class.getName() + "(" + StringUtil.notNullize(annotationParam) + ")", psiClass);
//        final PsiNameValuePair[] attributes = newAnnotation.getParameterList().getAttributes();
//
//          TODO implement me
//
//        PsiAnnotation psiAnnotation = modifierList.addAnnotation(Getter.class.getName());
//        psiAnnotation.setDeclaredAttributeValue(pair.getName(), pair.getValue());

//        javaCodeStyleManager.shortenClassReferences(psiAnnotation);

        psiMethod.delete();
      }

    }

    UndoUtil.markPsiFileForUndo(psiClass.getContainingFile());


    final PsiAnnotation psiAnnotation = PsiAnnotationUtil.findAnnotation(psiClass, getterProcessor.getSupportedAnnotation());
    if (null != psiAnnotation) {
      List<? super PsiElement> classGetters = getterProcessor.process(psiClass);
      createVanillaGetters(project, psiClass, classGetters);

      psiAnnotation.delete();
    }

    List<? super PsiElement> fieldGetters = getterFieldProcessor.process(psiClass);
    if (!fieldGetters.isEmpty()) {
      createVanillaGetters(project, psiClass, fieldGetters);
    }

    for (PsiField psiField : psiClass.getFields()) {
      final PsiAnnotation psiFieldAnnotation = PsiAnnotationUtil.findAnnotation(psiField, getterFieldProcessor.getSupportedAnnotation());
      if (null != psiFieldAnnotation) {
        psiFieldAnnotation.delete();
      }
    }
  }

  private void createVanillaGetters(Project project, PsiClass psiClass, List<? super PsiElement> classGetters) {
    for (Object psiElement : classGetters) {
      final PsiMethod lombokMethod = (PsiMethod) psiElement;

      final String propertyName = PropertyUtil.getPropertyName(lombokMethod);
      if (null != propertyName) {
        PsiField propertyField = PropertyUtil.findPropertyField(project, psiClass, propertyName, lombokMethod.hasModifierProperty(PsiModifier.STATIC));
        if (null != propertyField) {
          PsiMethod propertyGetter = PropertyUtil.generateGetterPrototype(propertyField);
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
