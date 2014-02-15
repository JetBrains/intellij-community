package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import de.plushnikov.intellij.plugin.processor.AbstractProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.field.AbstractFieldProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class BaseDelombokHandler {

  private final Collection<AbstractClassProcessor> classProcessors;
  private final Collection<AbstractFieldProcessor> fieldProcessors;

  protected BaseDelombokHandler(AbstractClassProcessor classProcessor, AbstractFieldProcessor fieldProcessor) {
    this(classProcessor);
    fieldProcessors.add(fieldProcessor);
  }

  protected BaseDelombokHandler(AbstractFieldProcessor fieldProcessor) {
    this();
    fieldProcessors.add(fieldProcessor);
  }

  protected BaseDelombokHandler(AbstractClassProcessor... classProcessors) {
    this.classProcessors = new ArrayList<AbstractClassProcessor>(Arrays.asList(classProcessors));
    this.fieldProcessors = new ArrayList<AbstractFieldProcessor>();
  }

  void addFieldProcessor(AbstractFieldProcessor... fieldProcessor) {
    fieldProcessors.addAll(Arrays.asList(fieldProcessor));
  }

  public void invoke(@NotNull Project project, @NotNull PsiJavaFile psiFile) {
    for (PsiClass psiClass : psiFile.getClasses()) {
      invoke(project, psiClass);

      for (PsiClass innerClass : psiClass.getAllInnerClasses()) {
        invoke(project, innerClass);
      }
    }
    finish(project, psiFile);
  }

  public void invoke(@NotNull Project project, @NotNull PsiFile psiFile, @NotNull PsiClass psiClass) {
    invoke(project, psiClass);
    finish(project, psiFile);
  }

  private void invoke(Project project, PsiClass psiClass) {
    for (AbstractClassProcessor classProcessor : classProcessors) {
      processClass(project, psiClass, classProcessor);
    }

    for (AbstractFieldProcessor fieldProcessor : fieldProcessors) {
      processFields(project, psiClass, fieldProcessor);
    }
  }

  private void finish(Project project, PsiFile psiFile) {
    JavaCodeStyleManager.getInstance(project).optimizeImports(psiFile);
    UndoUtil.markPsiFileForUndo(psiFile);
  }

  protected void processClass(@NotNull Project project, @NotNull PsiClass psiClass, AbstractProcessor classProcessor) {
    Collection<PsiAnnotation> psiAnnotations = classProcessor.collectProcessedAnnotations(psiClass);

    List<? super PsiElement> psiElements = classProcessor.process(psiClass);
    for (Object psiElement : psiElements) {
      psiClass.add(rebuildPsiElement(project, (PsiElement) psiElement));
    }

    deleteAnnotations(psiAnnotations);
  }

  public Collection<PsiAnnotation> collectProccessableAnnotations(@NotNull PsiClass psiClass) {
    Collection<PsiAnnotation> result = new ArrayList<PsiAnnotation>();

    for (AbstractClassProcessor classProcessor : classProcessors) {
      result.addAll(classProcessor.collectProcessedAnnotations(psiClass));
    }

    for (AbstractFieldProcessor fieldProcessor : fieldProcessors) {
      result.addAll(fieldProcessor.collectProcessedAnnotations(psiClass));
    }

    return result;
  }

  private void processFields(@NotNull Project project, @NotNull PsiClass psiClass, AbstractProcessor fieldProcessor) {
    Collection<PsiAnnotation> psiAnnotations = fieldProcessor.collectProcessedAnnotations(psiClass);

    List<? super PsiElement> psiElements = fieldProcessor.process(psiClass);
    for (Object psiElement : psiElements) {
      psiClass.add(rebuildPsiElement(project, (PsiMethod) psiElement));
    }

    deleteAnnotations(psiAnnotations);
  }

  private PsiElement rebuildPsiElement(@NotNull Project project, PsiElement psiElement) {
    if (psiElement instanceof PsiMethod) {
      return rebuildMethod(project, (PsiMethod) psiElement);
    } else if (psiElement instanceof PsiField) {
      return rebuildField(project, (PsiField) psiElement);
    } else if (psiElement instanceof PsiClass) {
      //TODO
    }
    return null;
  }

  private PsiMethod rebuildMethod(@NotNull Project project, @NotNull PsiMethod fromMethod) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

    final PsiMethod resultMethod;
    final PsiType returnType = fromMethod.getReturnType();
    if (null == returnType) {
      resultMethod = elementFactory.createConstructor(fromMethod.getName());
    } else {
      resultMethod = elementFactory.createMethod(fromMethod.getName(), returnType);
    }

    final PsiClassType[] referencedTypes = fromMethod.getThrowsList().getReferencedTypes();
    if (referencedTypes.length > 0) {
      PsiJavaCodeReferenceElement[] refs = new PsiJavaCodeReferenceElement[referencedTypes.length];
      for (int i = 0; i < refs.length; i++) {
        refs[i] = elementFactory.createReferenceElementByType(referencedTypes[i]);
      }
      resultMethod.getThrowsList().replace(elementFactory.createReferenceList(refs));
    }

    for (PsiParameter parameter : fromMethod.getParameterList().getParameters()) {
      PsiParameter param = elementFactory.createParameter(parameter.getName(), parameter.getType());
      resultMethod.getParameterList().add(param);
    }

    final PsiModifierList fromMethodModifierList = fromMethod.getModifierList();
    final PsiModifierList resultMethodModifierList = resultMethod.getModifierList();
    copyModifiers(fromMethodModifierList, resultMethodModifierList);
    for (PsiAnnotation psiAnnotation : fromMethodModifierList.getAnnotations()) {
      final PsiAnnotation annotation = resultMethodModifierList.addAnnotation(psiAnnotation.getQualifiedName());
      for (PsiNameValuePair nameValuePair : psiAnnotation.getParameterList().getAttributes()) {
        annotation.setDeclaredAttributeValue(nameValuePair.getName(), nameValuePair.getValue());
      }
    }

    PsiCodeBlock body = fromMethod.getBody();
    if (null != body) {
      resultMethod.getBody().replace(body);
    }

    return (PsiMethod) CodeStyleManager.getInstance(project).reformat(resultMethod);
  }

  private void copyModifiers(PsiModifierList fromModifierList, PsiModifierList resultModifierList) {
    for (String modifier : PsiModifier.MODIFIERS) {
      resultModifierList.setModifierProperty(modifier, fromModifierList.hasModifierProperty(modifier));
    }
  }

  private PsiField rebuildField(@NotNull Project project, @NotNull PsiField fromField) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

    final PsiField resultField = elementFactory.createField(fromField.getName(), fromField.getType());
    copyModifiers(fromField.getModifierList(), resultField.getModifierList());
    resultField.setInitializer(fromField.getInitializer());

    return (PsiField) CodeStyleManager.getInstance(project).reformat(resultField);
  }

  private void deleteAnnotations(Collection<PsiAnnotation> psiAnnotations) {
    for (PsiAnnotation psiAnnotation : psiAnnotations) {
      psiAnnotation.delete();
    }
  }
}
