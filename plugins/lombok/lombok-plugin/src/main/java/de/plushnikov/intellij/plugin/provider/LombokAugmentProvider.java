package de.plushnikov.intellij.plugin.provider;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.augment.PsiAugmentProvider;
import de.plushnikov.intellij.plugin.extension.LombokProcessorExtensionPoint;
import de.plushnikov.intellij.plugin.extension.UserMapKeys;
import de.plushnikov.intellij.plugin.processor.Processor;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

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
    final List<Psi> emptyResult = Collections.emptyList();
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

    boolean isLombokPresent = UserMapKeys.isLombokPossiblePresent(element);
    if (!isLombokPresent) {
      if (log.isDebugEnabled()) {
        log.debug(String.format("Skipped call for type: %s class: %s", type, ((PsiClass) element).getQualifiedName()));
      }
      return emptyResult;
    }

    return process(type, project, (PsiClass) element);
  }

  private <Psi extends PsiElement> List<Psi> process(Class<Psi> type, Project project, PsiClass psiClass) {
    boolean isLombokPossiblePresent = true;
    try {
      isLombokPossiblePresent = verifyLombokPresent(psiClass);
    } catch (IOException ex) {
      log.warn("Exception during check for Lombok", ex);
    }
    UserMapKeys.updateLombokPresent(psiClass, isLombokPossiblePresent);

    if (isLombokPossiblePresent) {
      if (log.isDebugEnabled()) {
        log.debug(String.format("Process call for type: %s class: %s", type, psiClass.getQualifiedName()));
      }

      cleanAttributeUsage(psiClass);

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

  private boolean verifyLombokPresent(PsiClass psiClass) throws IOException {
    boolean isLombokPossiblePresent = true;
    final PsiFile containingFile = psiClass.getContainingFile();
    if (null != containingFile) {
      VirtualFile virtualFile = containingFile.getVirtualFile();
      if (null != virtualFile) {
        InputStream inputStream = null;
        try {
          inputStream = virtualFile.getInputStream();
          Scanner scanner = new Scanner(inputStream);
          while (scanner.hasNextLine()) {
            final String lineFromFile = scanner.nextLine();
            isLombokPossiblePresent = lineFromFile.contains("lombok.");
            if (isLombokPossiblePresent) {
              break;
            }
          }
        } finally {
          if (null != inputStream) {
            inputStream.close();
          }
        }
      }
    }
    return isLombokPossiblePresent;
  }

  protected void cleanAttributeUsage(PsiClass psiClass) {
    for (PsiField psiField : PsiClassUtil.collectClassFieldsIntern(psiClass)) {
      UserMapKeys.removeAllUsagesFrom(psiField);
    }
  }

}
