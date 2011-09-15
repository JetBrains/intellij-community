package de.plushnikov.intellij.lombok.processor.field;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.util.StringBuilderSpinAllocator;
import de.plushnikov.intellij.lombok.processor.LombokProcessorUtil;
import de.plushnikov.intellij.lombok.psi.MyLightMethod;
import lombok.Getter;
import lombok.handlers.TransformationsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 *
 * @author Plushnikov Michail
 */
public class GetterFieldProcessor extends AbstractLombokFieldProcessor {

  public static final String CLASS_NAME = Getter.class.getName();

  public GetterFieldProcessor() {
    super(CLASS_NAME, PsiMethod.class);
  }

  public <Psi extends PsiElement> boolean process(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    boolean result = false;

    final String methodVisibity = LombokProcessorUtil.getMethodVisibity(psiAnnotation);
    if (null != methodVisibity) {
      Project project = psiField.getProject();

      PsiClass psiClass = psiField.getContainingClass();
      PsiManager manager = psiField.getContainingFile().getManager();
      PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

      target.add((Psi) createGetterMethod(psiField, methodVisibity, psiClass, manager, elementFactory));
      result = true;
    }
    //psiField.putUserData(UserMapKeys.READ_KEY, result);
    return result;
  }

  private PsiMethod createGetterMethod(PsiField psiField, String methodVisibility, PsiClass psiClass, PsiManager manager, PsiElementFactory elementFactory) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      final String fieldName = psiField.getName();
      final PsiType psiReturnType = psiField.getType();
      String methodName = TransformationsUtil.toGetterName(fieldName, PsiType.BOOLEAN.equals(psiReturnType));

      builder.append(methodVisibility);
      if (builder.length() > 0) {
        builder.append(' ');
      }
      builder.append(psiReturnType.getCanonicalText());
      builder.append(' ');
      builder.append(methodName);
      builder.append("()");
      builder.append("{ return this.").append(fieldName).append("; }");

      MyLightMethod result = new MyLightMethod(manager, elementFactory.createMethodFromText(builder.toString(), psiClass), psiClass);
      result.setNavigationElement(psiField);
      return result;


    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }


}
