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
import de.plushnikov.intellij.lombok.UserMapKeys;
import de.plushnikov.intellij.lombok.processor.LombokProcessorUtil;
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

    final String methodVisibity = LombokProcessorUtil.getMethodVisibity(psiAnnotation);
    if (null != methodVisibity) {
      Project project = psiField.getProject();
      PsiClass psiClass = psiField.getContainingClass();
      PsiManager manager = psiField.getContainingFile().getManager();
      PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

      target.add((Psi) createSetterMethod(psiField, methodVisibity, psiClass, manager, elementFactory));
      UserMapKeys.addWriteUsageFor(psiField);
      result = true;
    }
    return result;
  }

  private PsiMethod createSetterMethod(PsiField psiField, String methodVisibility, PsiClass psiClass, PsiManager manager, PsiElementFactory elementFactory) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      final String fieldName = psiField.getName();
      final PsiType psiFieldType = psiField.getType();
      final String methodName = TransformationsUtil.toSetterName(fieldName, PsiType.BOOLEAN.equals(psiFieldType));

      builder.append(methodVisibility);
      if (builder.length() > 0) {
        builder.append(' ');
      }
      builder.append(PsiType.VOID.getCanonicalText());
      builder.append(' ');
      builder.append(methodName);
      builder.append("(").append(psiFieldType.getCanonicalText()).append(' ').append(fieldName).append(')');
      builder.append("{ this.").append(fieldName).append(" = ").append(fieldName).append("; }");

      PsiMethod setterMethod = elementFactory.createMethodFromText(builder.toString(), psiClass);
      return prepareMethod(manager, setterMethod, psiClass, psiField);

    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }


}
