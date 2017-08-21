package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
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
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.PsiTypeParameterListOwner;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import de.plushnikov.intellij.plugin.processor.AbstractProcessor;
import de.plushnikov.intellij.plugin.processor.ShouldGenerateFullCodeBlock;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class BaseDelombokHandler {
  private final boolean processInnerClasses;
  private final Collection<AbstractProcessor> lombokProcessors;

  protected BaseDelombokHandler(AbstractProcessor... lombokProcessors) {
    this(false, lombokProcessors);
  }

  protected BaseDelombokHandler(boolean processInnerClasses, AbstractProcessor... lombokProcessors) {
    this.processInnerClasses = processInnerClasses;
    this.lombokProcessors = Arrays.asList(lombokProcessors);
  }

  public void invoke(@NotNull Project project, @NotNull PsiFile psiFile, @NotNull PsiClass psiClass) {
    if (psiFile.isWritable()) {
      invoke(project, psiClass, processInnerClasses);
      finish(project, psiFile);
    }
  }

  public void invoke(@NotNull Project project, @NotNull PsiJavaFile psiFile) {
    for (PsiClass psiClass : psiFile.getClasses()) {
      invoke(project, psiClass, true);
    }
    finish(project, psiFile);
  }

  private void invoke(Project project, PsiClass psiClass, boolean processInnerClasses) {
    Collection<PsiAnnotation> processedAnnotations = new HashSet<PsiAnnotation>();

    // get all inner classes before first lombok processing
    final PsiClass[] allInnerClasses = psiClass.getAllInnerClasses();

    for (AbstractProcessor lombokProcessor : lombokProcessors) {
      processedAnnotations.addAll(processClass(project, psiClass, lombokProcessor));
    }

    if (processInnerClasses) {
      for (PsiClass innerClass : allInnerClasses) {
        invoke(project, innerClass, processInnerClasses);
      }
    }
    deleteAnnotations(processedAnnotations);
  }

  private void finish(Project project, PsiFile psiFile) {
    JavaCodeStyleManager.getInstance(project).optimizeImports(psiFile);
    UndoUtil.markPsiFileForUndo(psiFile);
  }

  private Collection<PsiAnnotation> processClass(@NotNull Project project, @NotNull PsiClass psiClass, @NotNull AbstractProcessor lombokProcessor) {
    Collection<PsiAnnotation> psiAnnotations = lombokProcessor.collectProcessedAnnotations(psiClass);

    final List<? super PsiElement> psiElements;

    ShouldGenerateFullCodeBlock.getInstance().activate();
    try {
      psiElements = lombokProcessor.process(psiClass);
    } finally {
      ShouldGenerateFullCodeBlock.getInstance().deactivate();
    }

    ProjectSettings.setLombokEnabledInProject(project, false);
    try {
      for (Object psiElement : psiElements) {
        final PsiElement element = rebuildPsiElement(project, (PsiElement) psiElement);
        if (null != element) {
          psiClass.add(element);
        }
      }
    } finally {
      ProjectSettings.setLombokEnabledInProject(project, true);
    }

    return psiAnnotations;
  }

  public Collection<PsiAnnotation> collectProcessableAnnotations(@NotNull PsiClass psiClass) {
    Collection<PsiAnnotation> result = new ArrayList<PsiAnnotation>();

    for (AbstractProcessor lombokProcessor : lombokProcessors) {
      result.addAll(lombokProcessor.collectProcessedAnnotations(psiClass));
    }

    return result;
  }

  private PsiElement rebuildPsiElement(@NotNull Project project, PsiElement psiElement) {
    if (psiElement instanceof PsiMethod) {
      return rebuildMethod(project, (PsiMethod) psiElement);
    } else if (psiElement instanceof PsiField) {
      return rebuildField(project, (PsiField) psiElement);
    } else if (psiElement instanceof PsiClass) {
      return rebuildClass(project, (PsiClass) psiElement);
    }
    return null;
  }

  private PsiClass rebuildClass(@NotNull Project project, @NotNull PsiClass fromClass) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);

    PsiClass resultClass = elementFactory.createClass(StringUtil.defaultIfEmpty(fromClass.getName(), "UnknownClassName"));
    copyModifiers(fromClass.getModifierList(), resultClass.getModifierList());
    rebuildTypeParameter(fromClass, resultClass);

    for (PsiField psiField : fromClass.getFields()) {
      resultClass.add(rebuildField(project, psiField));
    }
    for (PsiMethod psiMethod : fromClass.getMethods()) {
      resultClass.add(rebuildMethod(project, psiMethod));
    }

    return (PsiClass) CodeStyleManager.getInstance(project).reformat(resultClass);
  }

  private PsiMethod rebuildMethod(@NotNull Project project, @NotNull PsiMethod fromMethod) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);

    final PsiMethod resultMethod;
    final PsiType returnType = fromMethod.getReturnType();
    if (null == returnType) {
      resultMethod = elementFactory.createConstructor(fromMethod.getName());
    } else {
      resultMethod = elementFactory.createMethod(fromMethod.getName(), returnType);
    }

    rebuildTypeParameter(fromMethod, resultMethod);

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
      if (parameter.getModifierList() != null) {
        PsiModifierList modifierList = param.getModifierList();
        for (PsiAnnotation originalAnnotation : parameter.getModifierList().getAnnotations()) {
          final PsiAnnotation annotation = modifierList.addAnnotation(originalAnnotation.getQualifiedName());
          for (PsiNameValuePair nameValuePair : originalAnnotation.getParameterList().getAttributes()) {
            annotation.setDeclaredAttributeValue(nameValuePair.getName(), nameValuePair.getValue());
          }
        }
      }
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

  private void rebuildTypeParameter(@NotNull PsiTypeParameterListOwner listOwner, @NotNull PsiTypeParameterListOwner resultOwner) {
    final PsiTypeParameterList fromMethodTypeParameterList = listOwner.getTypeParameterList();
    if (listOwner.hasTypeParameters() && null != fromMethodTypeParameterList) {
      PsiTypeParameterList typeParameterList = PsiMethodUtil.createTypeParameterList(fromMethodTypeParameterList);
      if (null != typeParameterList) {
        final PsiTypeParameterList resultOwnerTypeParameterList = resultOwner.getTypeParameterList();
        if (null != resultOwnerTypeParameterList) {
          resultOwnerTypeParameterList.replace(typeParameterList);
        }
      }
    }
  }

  private void copyModifiers(PsiModifierList fromModifierList, PsiModifierList resultModifierList) {
    for (String modifier : PsiModifier.MODIFIERS) {
      resultModifierList.setModifierProperty(modifier, fromModifierList.hasModifierProperty(modifier));
    }
  }

  private PsiField rebuildField(@NotNull Project project, @NotNull PsiField fromField) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);

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
