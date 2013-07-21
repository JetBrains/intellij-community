package de.plushnikov.intellij.plugin.provider;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.augment.PsiAugmentProvider;
import de.plushnikov.intellij.lombok.UserMapKeys;
import de.plushnikov.intellij.lombok.extension.LombokProcessorExtensionPoint;
import de.plushnikov.intellij.lombok.processor.LombokProcessor;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Provides support for lombok generated elements
 *
 * @author Plushnikov Michail
 */
public class LombokAugmentProvider extends PsiAugmentProvider {
  private static final Logger log = Logger.getInstance(LombokAugmentProvider.class.getName());

  public LombokAugmentProvider() {
    log.debug("LombokAugmentProvider created");
  }

  @NotNull
  @Override
  public <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element, @NotNull Class<Psi> type) {
    List<Psi> emptyResult = Collections.emptyList();
    // Expecting that we are only augmenting an PsiClass
    // Don't filter !isPhysical elements or code auto completion will not work
    if (!(element instanceof PsiClass) || !element.isValid()) {
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

    final PsiClass psiClass = (PsiClass) element;
    if (log.isDebugEnabled()) {
      log.debug(String.format("Process class %s with LombokAugmentProvider", psiClass.getName()));
    }

    cleanAttributeUsage(psiClass);

    List<Psi> result = new ArrayList<Psi>();
    for (LombokProcessor lombokProcessor : LombokProcessorExtensionPoint.EP_NAME.getExtensions()) {
      if (lombokProcessor.canProduce(type) && lombokProcessor.isEnabled(project)) {
        result.addAll((Collection<Psi>) lombokProcessor.process(psiClass));
      }
    }
    return result;
  }

  protected void cleanAttributeUsage(PsiClass psiClass) {
    for (PsiField psiField : PsiClassUtil.collectClassFieldsIntern(psiClass)) {
      UserMapKeys.removeAllUsagesFrom(psiField);
    }
  }

}
