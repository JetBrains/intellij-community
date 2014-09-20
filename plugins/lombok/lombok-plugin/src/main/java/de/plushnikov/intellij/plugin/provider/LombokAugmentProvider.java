package de.plushnikov.intellij.plugin.provider;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.augment.PsiAugmentProvider;
import de.plushnikov.intellij.plugin.extension.LombokProcessorExtensionPoint;
import de.plushnikov.intellij.plugin.extension.UserMapKeys;
import de.plushnikov.intellij.plugin.processor.Processor;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides support for lombok generated elements
 *
 * @author Plushnikov Michail
 */
public class LombokAugmentProvider extends PsiAugmentProvider {
  private static final Logger log = Logger.getInstance(LombokAugmentProvider.class.getName());

  private final static ThreadLocal<Set<AugmentCallData>> recursionBreaker = new ThreadLocal<Set<AugmentCallData>>() {
    @Override
    protected Set<AugmentCallData> initialValue() {
      return new HashSet<AugmentCallData>();
    }
  };

  private Collection<String> registeredAnnotationNames;

  public LombokAugmentProvider() {
    log.debug("LombokAugmentProvider created");
  }

  @NotNull
  @Override
  public <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element, @NotNull Class<Psi> type) {
    final List<Psi> emptyResult = Collections.emptyList();
    // Expecting that we are only augmenting an PsiClass
    // Don't filter !isPhysical elements or code auto completion will not work
    if (!(element instanceof PsiClass) || !element.isValid()) {
      return emptyResult;
    }
    // skip processing for other as supported types
    if (!(type.isAssignableFrom(PsiMethod.class) || type.isAssignableFrom(PsiField.class) || type.isAssignableFrom(PsiClass.class))) {
      return emptyResult;
    }

    final AugmentCallData currentAugmentData = new AugmentCallData(element, type);
    if (recursionBreaker.get().contains(currentAugmentData)) {
      log.debug("Prevented recursion call");
      return emptyResult;
    }

    // skip processing during index rebuild
    final Project project = element.getProject();
    if (DumbService.getInstance(project).isDumb()) {
      return emptyResult;
    }
    // skip processing if plugin is disabled
    if (!ProjectSettings.loadAndGetEnabledInProject(project)) {
      return emptyResult;
    }

    initRegisteredAnnotations();

    recursionBreaker.get().add(currentAugmentData);
    try {
      final PsiClass psiClass = (PsiClass) element;

      final boolean isLombokPresent = UserMapKeys.isLombokPossiblePresent(element);
      if (!isLombokPresent) {
        if (log.isDebugEnabled()) {
          log.debug(String.format("Skipped call for type: %s class: %s", type, psiClass.getQualifiedName()));
        }
        return emptyResult;
      }

      return process(type, project, psiClass);
    } finally {
      recursionBreaker.get().remove(currentAugmentData);
    }
  }

  private void initRegisteredAnnotations() {
    if (null == registeredAnnotationNames) {
      final Collection<String> nameSet = new HashSet<String>();

      for (Processor processor : LombokProcessorExtensionPoint.EP_NAME.getExtensions()) {
        Class<? extends Annotation> annotationClass = processor.getSupportedAnnotationClass();
        nameSet.add(annotationClass.getSimpleName());
        nameSet.add(annotationClass.getName());
      }

      registeredAnnotationNames = nameSet;
    }
  }

  private <Psi extends PsiElement> List<Psi> process(@NotNull Class<Psi> type, @NotNull Project project, @NotNull PsiClass psiClass) {
    final boolean isLombokPossiblePresent = verifyLombokPresent(psiClass);

    UserMapKeys.updateLombokPresent(psiClass, isLombokPossiblePresent);

    if (isLombokPossiblePresent) {
      if (log.isDebugEnabled()) {
        log.debug(String.format("Process call for type: %s class: %s", type, psiClass.getQualifiedName()));
      }

      final List<Psi> result = new ArrayList<Psi>();
      for (Processor processor : LombokProcessorExtensionPoint.EP_NAME.getExtensions()) {
        if (processor.canProduce(type) && processor.isEnabled(project)) {
          result.addAll((Collection<Psi>) processor.process(psiClass));
        }
      }
      return result;
    } else {
      if (log.isDebugEnabled()) {
        log.debug(String.format("Skipped call for type: %s class: %s", type, psiClass.getQualifiedName()));
      }
    }
    return Collections.emptyList();
  }

  private boolean verifyLombokPresent(@NotNull PsiClass psiClass) {
    if (PsiAnnotationUtil.checkAnnotationsSimpleNameExistsIn(psiClass, registeredAnnotationNames)) {
      return true;
    }
    Collection<PsiField> psiFields = PsiClassUtil.collectClassFieldsIntern(psiClass);
    for (PsiField psiField : psiFields) {
      if (PsiAnnotationUtil.checkAnnotationsSimpleNameExistsIn(psiField, registeredAnnotationNames)) {
        return true;
      }
    }
    Collection<PsiMethod> psiMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
    for (PsiMethod psiMethod : psiMethods) {
      if (PsiAnnotationUtil.checkAnnotationsSimpleNameExistsIn(psiMethod, registeredAnnotationNames)) {
        return true;
      }
    }

    return false;
  }

}
