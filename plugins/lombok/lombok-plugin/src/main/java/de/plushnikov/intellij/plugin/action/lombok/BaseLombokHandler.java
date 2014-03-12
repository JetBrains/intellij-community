package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class BaseLombokHandler implements CodeInsightActionHandler {

  protected abstract Class<? extends Annotation> getAnnotationClass();

  public boolean startInWriteAction() {
    return true;
  }

  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (file.isWritable()) {
      PsiClass psiClass = OverrideImplementUtil.getContextClass(project, editor, file, false);
      if (null != psiClass) {
        processClass(psiClass);

        UndoUtil.markPsiFileForUndo(file);
      }
    }
  }

  protected abstract void processClass(@NotNull PsiClass psiClass);

  protected void processIntern(@NotNull Map<PsiField, PsiMethod> fieldMethodMap, @NotNull PsiClass psiClass) {
    if (fieldMethodMap.isEmpty()) {
      return;
    }

    final PsiMethod firstPropertyMethod = fieldMethodMap.values().iterator().next();

    final boolean useAnnotationOnClass = haveAllMethodsSameAccessLevel(fieldMethodMap.values()) &&
        isNotAnnotatedWithOrSameAccessLevelAs(psiClass, firstPropertyMethod);

    if (useAnnotationOnClass) {
      addAnnotation(psiClass, firstPropertyMethod, getAnnotationClass());
    }

    for (Map.Entry<PsiField, PsiMethod> fieldMethodEntry : fieldMethodMap.entrySet()) {
      final PsiField propertyField = fieldMethodEntry.getKey();
      final PsiMethod propertyMethod = fieldMethodEntry.getValue();

      if (null != propertyField) {
        boolean isStatic = propertyField.hasModifierProperty(PsiModifier.STATIC);
        if (isStatic || !useAnnotationOnClass) {
          addAnnotation(propertyField, propertyMethod, getAnnotationClass());
        }

        propertyMethod.delete();
      }
    }
  }

  private boolean isNotAnnotatedWithOrSameAccessLevelAs(PsiClass psiClass, PsiMethod firstPropertyMethod) {
    final PsiAnnotation presentAnnotation = PsiAnnotationUtil.findAnnotation(psiClass, getAnnotationClass());
    if (null != presentAnnotation) {

      final String presentAccessModifier = LombokProcessorUtil.getMethodModifier(presentAnnotation);
      final String currentAccessModifier = PsiUtil.getAccessModifier(PsiUtil.getAccessLevel(firstPropertyMethod.getModifierList()));

      return (presentAccessModifier == null && currentAccessModifier == null) ||
          (presentAccessModifier != null && presentAccessModifier.equals(currentAccessModifier));
    }
    return true;
  }

  private boolean haveAllMethodsSameAccessLevel(Collection<PsiMethod> psiMethods) {
    final Set<Integer> accessLevelSet = new HashSet<Integer>();
    for (PsiMethod psiMethod : psiMethods) {
      accessLevelSet.add(PsiUtil.getAccessLevel(psiMethod.getModifierList()));
    }
    return accessLevelSet.size() <= 1;
  }

  private void addAnnotation(@NotNull PsiModifierListOwner targetElement, @NotNull PsiModifierListOwner sourceElement,
                             @NotNull Class<? extends Annotation> annotationClass) {
    final PsiAnnotation newPsiAnnotation = LombokProcessorUtil.createAnnotationWithAccessLevel(annotationClass, sourceElement);

    addAnnotation(targetElement, newPsiAnnotation, annotationClass);
  }

  protected void addAnnotation(@NotNull PsiModifierListOwner targetElement, @NotNull Class<? extends Annotation> annotationClass) {
    final PsiAnnotation newPsiAnnotation = PsiAnnotationUtil.createPsiAnnotation(targetElement, annotationClass);

    addAnnotation(targetElement, newPsiAnnotation, annotationClass);
  }

  private void addAnnotation(@NotNull PsiModifierListOwner targetElement, @NotNull PsiAnnotation newPsiAnnotation,
                             @NotNull Class<? extends Annotation> annotationClass) {
    final PsiAnnotation presentAnnotation = PsiAnnotationUtil.findAnnotation(targetElement, annotationClass);

    final Project project = targetElement.getProject();
    final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
    javaCodeStyleManager.shortenClassReferences(newPsiAnnotation);

    if (null == presentAnnotation) {
      PsiModifierList modifierList = targetElement.getModifierList();
      if (null != modifierList) {
        modifierList.addAfter(newPsiAnnotation, null);
      }
    } else {
      presentAnnotation.setDeclaredAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME,
          newPsiAnnotation.findDeclaredAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME));
    }
  }
}
