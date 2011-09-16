package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import de.plushnikov.intellij.lombok.UserMapKeys;
import de.plushnikov.intellij.lombok.processor.LombokProcessorUtil;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class RequiredArgsConstructorProcessor extends AbstractConstructorClassProcessor {

  private static final String CLASS_NAME = RequiredArgsConstructor.class.getName();
  public static final String NOT_NULL_ANNOTAION = NonNull.class.getName().toLowerCase();

  public RequiredArgsConstructorProcessor() {
    super(CLASS_NAME, PsiMethod.class);
  }

  public <Psi extends PsiElement> void process(@NotNull PsiClass psiClass, @NotNull PsiMethod[] classMethods, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    Project project = psiClass.getProject();
    PsiManager manager = psiClass.getContainingFile().getManager();
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

    final String visibility = LombokProcessorUtil.getAccessVisibity(psiAnnotation);
    if (null != visibility) {
      Collection<PsiField> allReqFields = getRequiredFields(psiClass);

      PsiMethod constructorMethod = createConstructorMethod(visibility, allReqFields, psiClass, elementFactory);
      target.add((Psi) prepareMethod(manager, constructorMethod, psiClass, psiAnnotation));

      for (PsiField psiField : allReqFields) {
        UserMapKeys.addWriteUsageFor(psiField);
      }
    }
  }

  @NotNull
  protected Collection<PsiField> getRequiredFields(@NotNull PsiClass psiClass) {
    Collection<PsiField> result = new ArrayList<PsiField>();
    for (PsiField psiField : getAllNotInitializedAndNotStaticFields(psiClass)) {
      boolean addField = false;
      // skip initialized fields
      if (null == psiField.getInitializer()) {
        PsiModifierList modifierList = psiField.getModifierList();
        if (null != modifierList) {
          // take only final or @NotNull fields
          if (!modifierList.hasModifierProperty(PsiModifier.FINAL)) {
            for (PsiAnnotation psiAnnotation : modifierList.getAnnotations()) {
              final String qualifiedName = StringUtil.notNullize(StringUtil.toLowerCase(psiAnnotation.getQualifiedName()));
              addField |= qualifiedName.endsWith(NOT_NULL_ANNOTAION);
            }

          } else {
            // Field is final
            addField = true;
          }
        }
      }

      if (addField) {
        result.add(psiField);
      }
    }
    return result;
  }

}
