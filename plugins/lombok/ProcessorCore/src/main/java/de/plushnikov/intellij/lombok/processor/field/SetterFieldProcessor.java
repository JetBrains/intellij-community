package de.plushnikov.intellij.lombok.processor.field;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import de.plushnikov.intellij.lombok.UserMapKeys;
import de.plushnikov.intellij.lombok.processor.LombokProcessorUtil;
import de.plushnikov.intellij.lombok.psi.MyLightMethod;
import lombok.Setter;
import lombok.handlers.TransformationsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 *
 * @author Plushnikov Michail
 */
public class SetterFieldProcessor extends AbstractLombokFieldProcessor {

  public static final String CLASS_NAME = Setter.class.getName();

  public SetterFieldProcessor() {
    super(CLASS_NAME, PsiMethod.class);
  }

  public <Psi extends PsiElement> boolean process(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    boolean result = false;

    final String visibility = LombokProcessorUtil.getMethodVisibity(psiAnnotation);
    if (null != visibility) {
      Project project = psiField.getProject();
      PsiClass psiClass = psiField.getContainingClass();
      PsiManager manager = psiField.getContainingFile().getManager();
      PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

      String fieldName = psiField.getName();
      PsiType psiType = psiField.getType();
      String typeName = psiType.getCanonicalText();
      String setterName = TransformationsUtil.toSetterName(fieldName, PsiType.BOOLEAN.equals(psiType));

      final PsiMethod valuesMethod = elementFactory.createMethodFromText(
          visibility + "void " + setterName + "(" + typeName + " " + fieldName + ") {this." + fieldName + "=" + fieldName + ";}",
          psiClass);

      target.add((Psi) new MyLightMethod(manager, valuesMethod, psiClass));
      result = true;
    }
    psiField.putUserData(UserMapKeys.WRITE_KEY, result);
    return result;
  }


}
