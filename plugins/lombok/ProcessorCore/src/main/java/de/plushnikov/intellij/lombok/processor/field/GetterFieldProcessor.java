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
import lombok.Getter;
import lombok.handlers.TransformationsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
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

  public <Psi extends PsiElement> void process(@NotNull PsiField psiField, @NotNull PsiMethod[] classMethods, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    final String methodVisibity = LombokProcessorUtil.getMethodVisibity(psiAnnotation);
    if (null != methodVisibity) {
      Project project = psiField.getProject();

      PsiClass psiClass = psiField.getContainingClass();
      PsiManager manager = psiField.getContainingFile().getManager();
      PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

      PsiMethod getterMethod = createGetterMethod(psiField, methodVisibity, psiClass, manager, elementFactory);
      if (!hasMethodByName(classMethods, getterMethod)) {//TODO check all getter Names
        target.add((Psi) getterMethod);
        UserMapKeys.addReadUsageFor(psiField);
      } else {
        //TODO create warning in code
        //Not generating methodName(): A method with that name already exists
      }
    }
  }

  private PsiMethod createGetterMethod(PsiField psiField, String methodVisibility, PsiClass psiClass, PsiManager manager, PsiElementFactory elementFactory) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      final String fieldName = psiField.getName();
      final PsiType psiReturnType = psiField.getType();
      String methodName = TransformationsUtil.toGetterName(fieldName, PsiType.BOOLEAN.equals(psiReturnType));

      final Collection<String> annotationsToCopy = collectAnnotationsToCopy(psiField);
      final String annotationsString = buildAnnotationsString(annotationsToCopy);

      builder.append(methodVisibility);
      if (builder.length() > 0) {
        builder.append(' ');
      }
      builder.append(annotationsString);
      builder.append(psiReturnType.getCanonicalText());
      builder.append(' ');
      builder.append(methodName);
      builder.append("()");
      builder.append("{ return this.").append(fieldName).append("; }");

      PsiMethod getterMethod = elementFactory.createMethodFromText(builder.toString(), psiClass);
      return prepareMethod(manager, getterMethod, psiClass, psiField);
    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }


}
