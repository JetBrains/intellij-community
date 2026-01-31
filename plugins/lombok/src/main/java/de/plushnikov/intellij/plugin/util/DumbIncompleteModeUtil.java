package de.plushnikov.intellij.plugin.util;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IncompleteDependenciesService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public final class DumbIncompleteModeUtil {
  private DumbIncompleteModeUtil() {
  }

  public static boolean isIncompleteMode(@NotNull Project project) {
    return Registry.is("lombok.incomplete.mode.enabled", false) &&
           !project.getService(IncompleteDependenciesService.class).getState().isComplete();
  }

  public static boolean isDumbOrIncompleteMode(@NotNull PsiElement context) {
    Project project = context.getProject();
    return isDumbOrIncompleteMode(project);
  }

  public static boolean isDumbOrIncompleteMode(@NotNull Project project) {
    return (DumbService.isDumb(project) && Registry.is("lombok.dumb.mode.enabled", false)) ||
           isIncompleteMode(project);
  }

  /**
   * Searches for a specific annotation in the list of annotations of a PsiModifierListOwner in dumb mode.
   *
   * @param owner the PsiModifierListOwner whose annotations to search.
   * @param annotationFQN the fully qualified name of the annotation to search for.
   * @return the found PsiAnnotation object if the annotation is found, or null if not found.
   */
  static @Nullable PsiAnnotation findAnnotationInDumbOrIncompleteMode(@NotNull PsiModifierListOwner owner, @NotNull String annotationFQN) {
    for (PsiAnnotation annotation : owner.getAnnotations()) {
      if (hasQualifiedNameInDumbOrIncompleteMode(annotation, annotationFQN)) {
        return annotation;
      }
    }
    return null;
  }

  /**
   * Finds the fully qualified name of a Lombok annotation based only on psi structure, without resolving.
   * Only annotations which have processors are supported
   *
   * @param psiAnnotation the PsiAnnotation object representing the Lombok annotation
   * @return the fully qualified name of the Lombok annotation, or null if the annotation is unresolved or not a Lombok annotation
   */
  public static @Nullable String findLombokAnnotationQualifiedNameInDumbIncompleteMode(@NotNull PsiAnnotation psiAnnotation) {
    String qualifiedName = psiAnnotation.getQualifiedName();
    if (StringUtil.isEmpty(qualifiedName)) return null;
    if (qualifiedName.startsWith("lombok")) return qualifiedName;
    LombokProcessorManager instance = LombokProcessorManager.getInstance();
    MultiMap<String, String> names = instance.getOurSupportedShortNames();
    Collection<String> fullQualifiedNames = names.get(qualifiedName);
    for (String fullQualifiedName : fullQualifiedNames) {
      if (hasQualifiedNameInDumbOrIncompleteMode(psiAnnotation, fullQualifiedName)) {
        return fullQualifiedName;
      }
    }
    return qualifiedName;
  }

  /**
   * Checks if the given annotation has a qualified name in dumb mode.
   * It is not fully accurate because it can't do resolving
   *
   * @param annotation the PsiAnnotation object to check.
   * @param fqn the fully qualified name to check against.
   * @return true if the annotation has the specified qualified name in dumb mode, otherwise false.
   */
  static boolean hasQualifiedNameInDumbOrIncompleteMode(PsiAnnotation annotation, @NotNull String fqn) {
    PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
    if (referenceElement == null) return false;
    String annotationReferenceName = referenceElement.getReferenceName();
    if (annotationReferenceName == null) return false;
    if (annotationReferenceName.equals(fqn) || ("java.lang." + annotationReferenceName).equals(fqn)) return true;
    String referenceElementText = referenceElement.getText();
    if (!StringUtil.isShortNameOf(fqn, annotationReferenceName)) return false;
    if (referenceElementText != null && referenceElementText.equals(fqn)) return true;
    PsiFile containingFile = annotation.getContainingFile();
    if (!(containingFile instanceof PsiJavaFile javaFile)) {
      return false;
    }
    String packageName = StringUtil.getPackageName(fqn);
    PsiImportList importList = javaFile.getImportList();
    if (importList == null) return false;
    int indexMayByOuterClass = fqn.length() - annotationReferenceName.length() - 1;
    String mayBeOuterClass = indexMayByOuterClass > 0 ? fqn.substring(0, indexMayByOuterClass) : null;
    return importList.findOnDemandImportStatement(packageName) != null ||
           importList.findSingleClassImportStatement(fqn) != null ||
           (mayBeOuterClass != null && importList.findSingleClassImportStatement(mayBeOuterClass) != null);
  }

  /**
   * Checks if the project is in incomplete mode and the class contains Lombok annotation.
   * Incomplete mode means that the project contains incomplete dependencies.
   * There is no purpose to be absolutely accurate, but it can help to reduce false red-code highlighting and help with some completion
   * This method can be quite slow, but it calls only in incomplete mode and cache value for file
   * @param context  The PsiElement to check for Lombok annotations.
   * @return true if the project is in incomplete mode and the class has any Lombok annotation or if any of the class fields have any Lombok annotation;
   * otherwise, false.
   */
  public static boolean isIncompleteModeWithLombokAnnotation(@NotNull PsiElement context) {
    if (!isIncompleteMode(context.getProject())) {
      return false;
    }

    if (context.getLanguage() != JavaLanguage.INSTANCE) {
      return false;
    }

    if (context instanceof PsiModifierList modifierList && hasAnyFullyQualifiedLombokAnnotation(modifierList.getAnnotations())) {
      return true;
    }

    PsiClass psiClass = PsiTreeUtil.getNonStrictParentOfType(context, PsiClass.class);

    if (psiClass == null) return false;

    return CachedValuesManager.getProjectPsiDependentCache(psiClass, psiElement -> {
      if (!(psiElement.getContainingFile() instanceof PsiJavaFile file)) {
        return false;
      }
      if (file.getImportList() != null && ContainerUtil.exists(file.getImportList().getAllImportStatements(), statement -> {
        return canBeLombokImport(statement);
      })) {
        return true;
      }
      while (psiElement != null) {
        if (psiElement instanceof PsiExtensibleClass extensibleClass &&
            (hasAnyFullyQualifiedLombokAnnotation(extensibleClass.getAnnotations()) ||
             ContainerUtil.exists(extensibleClass.getOwnFields(), field -> hasAnyFullyQualifiedLombokAnnotation(field.getAnnotations())) ||
             ContainerUtil.exists(extensibleClass.getOwnMethods(), method -> hasAnyFullyQualifiedLombokAnnotation(method.getAnnotations())) ||
             (file.getImportList() != null && ContainerUtil.exists(file.getImportList().getAllImportStatements(), statement -> {
               return canBeLombokImport(statement);
             })))) {
          return true;
        }
        psiElement = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
      }
      return false;
    });
  }

  private static boolean canBeLombokImport(@NotNull PsiImportStatementBase statement) {
    PsiJavaCodeReferenceElement reference = statement.getImportReference();
    return reference != null && reference.getText().startsWith("lombok");
  }

  /**
   * @param annotations the array of annotations to check
   * @return true if any of the annotations is a fully qualified Lombok annotation, false otherwise
   */
  private static boolean hasAnyFullyQualifiedLombokAnnotation(PsiAnnotation @NotNull [] annotations) {
    return ContainerUtil.exists(annotations, annotation -> {
      if (annotation == null) {
        return false;
      }
      String qualifiedName = annotation.getText();
      if (qualifiedName == null) {
        return false;
      }
      return qualifiedName.startsWith("@lombok.");
    });
  }
}
