package de.plushnikov.intellij.plugin.provider;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import de.plushnikov.intellij.plugin.extension.LombokProcessorExtensionPoint;
import de.plushnikov.intellij.plugin.extension.UserMapKeys;
import de.plushnikov.intellij.plugin.processor.Processor;
import de.plushnikov.intellij.plugin.processor.ValProcessor;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Provides support for lombok generated elements
 *
 * @author Plushnikov Michail
 */
public class LombokAugmentProvider extends PsiAugmentProvider {
  private static final Logger log = Logger.getInstance(LombokAugmentProvider.class.getName());

  private Collection<String> registeredAnnotationNames;
  private ValProcessor valProcessor;

  public LombokAugmentProvider() {
    log.debug("LombokAugmentProvider created");
    valProcessor = new ValProcessor();
  }

  @NotNull
  @Override
  public <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element, @NotNull Class<Psi> type) {
    final List<Psi> emptyResult = Collections.emptyList();
    // skip processing during index rebuild
    final Project project = element.getProject();
    if (DumbService.isDumb(project)) {
      return emptyResult;
    }
    // Expecting that we are only augmenting an PsiClass
    // Don't filter !isPhysical elements or code auto completion will not work
    if (!(element instanceof PsiExtensibleClass) || !element.isValid()) {
      return emptyResult;
    }
    // skip processing for other as supported types
    if (type != PsiMethod.class && type != PsiField.class && type != PsiClass.class) {
      return emptyResult;
    }
    // skip processing if plugin is disabled
    if (!ProjectSettings.loadAndGetEnabledInProject(project)) {
      return emptyResult;
    }

    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) {
      return emptyResult;
    }

    initRegisteredAnnotations();

    final PsiClass psiClass = (PsiClass) element;

    boolean fileOpenInEditor = true;

    final VirtualFile virtualFile = containingFile.getVirtualFile();
    if (null != virtualFile) {
      fileOpenInEditor = FileEditorManager.getInstance(project).isFileOpen(virtualFile);
    }

    if (fileOpenInEditor || checkLombokPresent(psiClass)) {
      return process(type, project, psiClass);
    }

    return emptyResult;
  }

  private boolean checkLombokPresent(PsiClass psiClass) {
    boolean result = UserMapKeys.isLombokPossiblePresent(psiClass);
    if (result) {
      result = verifyLombokAnnotationPresent(psiClass);
    }
    UserMapKeys.updateLombokPresent(psiClass, result);
    return result;
  }

  @Nullable
  protected PsiType inferType(PsiTypeElement typeElement) {
    if (null == typeElement || DumbService.isDumb(typeElement.getProject())) {
      return null;
    }
    return valProcessor.inferType(typeElement);
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
  }

  private boolean verifyLombokAnnotationPresent(@NotNull PsiClass psiClass) {
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
    final PsiElement psiClassParent = psiClass.getParent();
    if (psiClassParent instanceof PsiClass) {
      return verifyLombokAnnotationPresent((PsiClass) psiClassParent);
    }

    return false;
  }

}
