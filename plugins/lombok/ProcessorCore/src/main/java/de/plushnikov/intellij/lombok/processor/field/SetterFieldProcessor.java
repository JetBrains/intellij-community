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

import java.util.Collection;
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

  public <Psi extends PsiElement> void process(@NotNull PsiField psiField, @NotNull PsiMethod[] classMethods, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    final String methodVisibity = LombokProcessorUtil.getMethodVisibity(psiAnnotation);
    if (null != methodVisibity) {
      Project project = psiField.getProject();
      PsiClass psiClass = psiField.getContainingClass();
      PsiManager manager = psiField.getContainingFile().getManager();
      PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

      PsiMethod setterMethod = createSetterMethod(psiField, methodVisibity, psiClass, manager, elementFactory);
      if (!hasMethodByName(classMethods, setterMethod)) {//TODO check all setter Names
        target.add((Psi) setterMethod);
        UserMapKeys.addWriteUsageFor(psiField);
      } else {
        //TODO create warning in code
        //Not generating methodName(): A method with that name already exists
      }
    }
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

      final Collection<String> annotationsToCopy = collectAnnotationsToCopy(psiField);
      final String annotationsString = buildAnnotationsString(annotationsToCopy);

      builder.append("(").append(annotationsString).append(psiFieldType.getCanonicalText()).append(' ').append(fieldName).append(')');
      builder.append("{ this.").append(fieldName).append(" = ").append(fieldName).append("; }");

      PsiMethod setterMethod = elementFactory.createMethodFromText(builder.toString(), psiClass);
      return prepareMethod(manager, setterMethod, psiClass, psiField);

    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }


}
