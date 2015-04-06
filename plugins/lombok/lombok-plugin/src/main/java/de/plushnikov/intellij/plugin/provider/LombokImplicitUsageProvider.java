package de.plushnikov.intellij.plugin.provider;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;

/**
 * Provides implicit usages of lombok fields
 */
@SuppressWarnings("deprecation")
public class LombokImplicitUsageProvider implements ImplicitUsageProvider {

  private static final List<Class<? extends Annotation>> ANNOTATIONS = Arrays.asList(
      lombok.experimental.Accessors.class, lombok.experimental.Builder.class, lombok.experimental.Delegate.class,
      lombok.experimental.ExtensionMethod.class, lombok.experimental.FieldDefaults.class,
      lombok.experimental.NonFinal.class, lombok.experimental.PackagePrivate.class,
      lombok.experimental.Tolerate.class, lombok.experimental.UtilityClass.class,
      lombok.experimental.Value.class, lombok.experimental.Wither.class,
      lombok.AllArgsConstructor.class, lombok.Builder.class,
      lombok.Cleanup.class, lombok.Data.class,
      lombok.Delegate.class, lombok.EqualsAndHashCode.class,
      lombok.Getter.class, lombok.NoArgsConstructor.class,
      lombok.NonNull.class, lombok.RequiredArgsConstructor.class,
      lombok.Setter.class, lombok.SneakyThrows.class,
      lombok.Synchronized.class, lombok.ToString.class,
      lombok.Value.class);

  private static final Collection<String> FIELD_ANNOTATIONS = new HashSet<String>();
  private static final Collection<String> METHOD_ANNOTATIONS = new HashSet<String>();
  private static final Collection<String> CLASS_ANNOTATIONS = new HashSet<String>();

  public LombokImplicitUsageProvider() {
    for (Class<? extends Annotation> annotation : ANNOTATIONS) {
      final EnumSet<ElementType> elementTypes = EnumSet.copyOf(Arrays.asList(annotation.getAnnotation(Target.class).value()));
      if (elementTypes.contains(ElementType.FIELD)) {
        FIELD_ANNOTATIONS.add(annotation.getName());
      }
      if (elementTypes.contains(ElementType.METHOD) || elementTypes.contains(ElementType.CONSTRUCTOR)) {
        METHOD_ANNOTATIONS.add(annotation.getName());
      }
      if (elementTypes.contains(ElementType.TYPE)) {
        CLASS_ANNOTATIONS.add(annotation.getName());
      }
    }
  }

  @Override
  public boolean isImplicitUsage(PsiElement element) {
    return checkUsage(element);
  }

  @Override
  public boolean isImplicitRead(PsiElement element) {
    return checkUsage(element);
  }

  @Override
  public boolean isImplicitWrite(PsiElement element) {
    return checkUsage(element);
  }

  private boolean checkUsage(PsiElement element) {
    boolean result = false;
    if (element instanceof PsiField) {
      result = checkAnnotations((PsiModifierListOwner) element, FIELD_ANNOTATIONS);
    } else if (element instanceof PsiMethod) {
      result = checkAnnotations((PsiModifierListOwner) element, METHOD_ANNOTATIONS);
    }
    return result;
  }

  private boolean checkAnnotations(PsiModifierListOwner element, Collection<String> annotations) {
    boolean result;
    result = AnnotationUtil.isAnnotated(element, annotations);
    if (!result) {
      final PsiClass containingClass = ((PsiMember) element).getContainingClass();
      if (null != containingClass) {
        result = AnnotationUtil.isAnnotated(containingClass, CLASS_ANNOTATIONS);
      }
    }
    return result;
  }
}
