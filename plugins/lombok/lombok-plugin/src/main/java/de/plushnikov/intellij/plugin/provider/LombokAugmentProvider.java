package de.plushnikov.intellij.plugin.provider;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.augment.PsiAugmentProvider;
import de.plushnikov.intellij.plugin.extension.LombokProcessorExtensionPoint;
import de.plushnikov.intellij.plugin.extension.UserMapKeys;
import de.plushnikov.intellij.plugin.processor.Processor;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;

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
  private static final String LOMBOK_PREFIX_MARKER = "lombok.";

  private final static ThreadLocal<Set<AugmentCallData>> recursionBreaker = new ThreadLocal<Set<AugmentCallData>>() {
    @Override
    protected Set<AugmentCallData> initialValue() {
      return new HashSet<AugmentCallData>();
    }
  };

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

    recursionBreaker.get().add(currentAugmentData);
    try {
      final PsiClass psiClass = (PsiClass) element;

      final boolean isLombokPresent = UserMapKeys.isLombokPossiblePresent(element) || checkImportSection(psiClass);
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

  private boolean checkImportSection(@NotNull PsiClass psiClass) {
    if (psiClass instanceof PsiJavaFile) {
      PsiJavaFile psiFile = (PsiJavaFile) psiClass.getContainingFile();

      PsiImportList psiImportList = psiFile.getImportList();
      if (null != psiImportList) {
        for (PsiImportStatementBase psiImportStatementBase : psiImportList.getAllImportStatements()) {
          PsiJavaCodeReferenceElement importReference = psiImportStatementBase.getImportReference();
          String qualifiedName = StringUtil.notNullize(null == importReference ? "" : importReference.getQualifiedName());
          if (qualifiedName.startsWith(LOMBOK_PREFIX_MARKER)) {
            return true;
          }
        }
        return false;
      }
    }
    return true;
  }

  private boolean verifyLombokPresent(@NotNull PsiClass psiClass) {
    if (checkAnnotations(psiClass)) {
      return true;
    }
    Collection<PsiField> psiFields = PsiClassUtil.collectClassFieldsIntern(psiClass);
    for (PsiField psiField : psiFields) {
      if (checkAnnotations(psiField)) {
        return true;
      }
    }
    Collection<PsiMethod> psiMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
    for (PsiMethod psiMethod : psiMethods) {
      if (checkAnnotations(psiMethod)) {
        return true;
      }
    }

    return false;
  }

  private boolean checkAnnotations(@NotNull PsiModifierListOwner modifierListOwner) {
    PsiModifierList modifierList = modifierListOwner.getModifierList();
    if (null != modifierList) {
      for (PsiAnnotation psiAnnotation : modifierList.getAnnotations()) {
        String qualifiedName = StringUtil.notNullize(psiAnnotation.getQualifiedName());
        if (qualifiedName.startsWith(LOMBOK_PREFIX_MARKER)) {
          return true;
        }
      }
    }
    return false;
  }
}
