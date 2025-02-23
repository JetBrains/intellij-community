package de.plushnikov.intellij.plugin.extension;

import com.intellij.java.codeserver.highlighting.JavaErrorFilter;
import com.intellij.java.codeserver.highlighting.errors.JavaCompilationError;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Pattern;

public final class LombokHighlightErrorFilter implements JavaErrorFilter {
  private static final Pattern UNDERSCORES = Pattern.compile("_+");

  private static final Collection<String> ONXABLE_ANNOTATIONS = Arrays.asList(
    LombokClassNames.GETTER,
    LombokClassNames.SETTER,
    LombokClassNames.WITH,
    LombokClassNames.WITHER,
    LombokClassNames.NO_ARGS_CONSTRUCTOR,
    LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR,
    LombokClassNames.ALL_ARGS_CONSTRUCTOR,
    LombokClassNames.EQUALS_AND_HASHCODE
  );
  private static final Collection<String> ONX_PARAMETERS = Arrays.asList(
    "onConstructor",
    "onMethod",
    "onParam"
  );

  public LombokHighlightErrorFilter() {
  }

  @Override
  public boolean shouldSuppressError(@NotNull PsiFile file, @NotNull JavaCompilationError<?, ?> error) {
    Project project = file.getProject();
    return LombokLibraryUtil.hasLombokLibrary(project) && isOnXParameterAnnotation(error);
  }

  private static boolean isOnXParameterAnnotation(@NotNull JavaCompilationError<?, ?> error) {
    if (!isMyError(error)) return false;

    PsiElement highlightedElement = error.psi();

    PsiNameValuePair nameValuePair = findContainingNameValuePair(highlightedElement);
    if (nameValuePair == null || !(nameValuePair.getContext() instanceof PsiAnnotationParameterList)) {
      return false;
    }

    String parameterName = nameValuePair.getName();
    if (null != parameterName && parameterName.contains("_")) {
      parameterName = parameterName.substring(0, parameterName.indexOf('_'));
    }
    if (!ONX_PARAMETERS.contains(parameterName)) {
      return false;
    }

    PsiElement containingAnnotation = nameValuePair.getContext().getContext();
    return containingAnnotation instanceof PsiAnnotation annotation &&
           ONXABLE_ANNOTATIONS.contains(annotation.getQualifiedName());
  }

  private static boolean isMyError(@NotNull JavaCompilationError<?, ?> error) {
    return error.kind() == JavaErrorKinds.ANNOTATION_TYPE_EXPECTED ||
           error.forKind(JavaErrorKinds.REFERENCE_UNRESOLVED)
             .filter(e -> isUnderscore(e.psi())).isPresent() ||
           error.forKind(JavaErrorKinds.ANNOTATION_ATTRIBUTE_INCOMPATIBLE_TYPE)
             .filter(e -> isAnyAnnotation(e.context().expectedType()) &&
                          e.psi() instanceof PsiAnnotation anno && isUnderscore(anno.getNameReferenceElement())).isPresent();
  }

  private static boolean isUnderscore(@Nullable PsiJavaCodeReferenceElement e) {
    return e != null && e.getReferenceName() != null && UNDERSCORES.matcher(e.getReferenceName()).matches();
  }

  private static boolean isAnyAnnotation(@NotNull PsiType type) {
    return type instanceof PsiArrayType arrayType &&
           arrayType.getComponentType() instanceof PsiClassType classType &&
           classType.getClassName().equals("AnyAnnotation") &&
           classType.getCanonicalText().startsWith("lombok.");
  }

  private static PsiNameValuePair findContainingNameValuePair(PsiElement highlightedElement) {
    PsiElement nameValuePair = highlightedElement;
    while (!(nameValuePair == null || nameValuePair instanceof PsiNameValuePair)) {
      nameValuePair = nameValuePair.getContext();
    }

    return (PsiNameValuePair)nameValuePair;
  }
}
