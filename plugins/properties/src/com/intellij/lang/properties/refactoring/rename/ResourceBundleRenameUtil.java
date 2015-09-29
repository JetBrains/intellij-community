/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties.refactoring.rename;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameProcessor;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class ResourceBundleRenameUtil {
  private final static Logger LOG = Logger.getInstance(ResourceBundleRenameUtil.class);

  public static void renameResourceBundleKey(final @NotNull PsiElement psiElement, final @NotNull Project project) {
    if (psiElement.isValid()) {
      PsiElementRenameHandler.invoke(psiElement, project, psiElement.getContainingFile(), null);
    }
  }

  public static void renameResourceBundleBaseName(final @NotNull ResourceBundle resourceBundle, final @NotNull Project project) {
    Messages.showInputDialog(project, PropertiesBundle.message("rename.bundle.enter.new.resource.bundle.base.name.prompt.text"),
                             PropertiesBundle.message("rename.resource.bundle.dialog.title"), Messages.getQuestionIcon(),
                             resourceBundle.getBaseName(), new ResourceBundleBaseNameInputValidator(project, resourceBundle));
  }

  public static void renameResourceBundleKeySection(final List<PsiElement> psiElements, final String section, final int sectionPosition) {
    if (psiElements.isEmpty()) {
      return;
    }
    final Project project = psiElements.get(0).getProject();
    Messages.showInputDialog(project, PropertiesBundle.message("rename.bundle.enter.new.resource.bundle.section.name.prompt.text"),
                             PropertiesBundle.message("rename.resource.bundle.section.dialog.title"), Messages.getQuestionIcon(), section,
                             new ResourceBundleKeySectionInputValidator(psiElements, section, sectionPosition, project));
  }

  private static class ResourceBundleKeySectionInputValidator implements InputValidator {

    private final List<PsiElement> myPsiElements;
    private final String mySection;
    private final int mySectionPosition;
    private final Project myProject;

    private ResourceBundleKeySectionInputValidator(final List<PsiElement> psiElements,
                                                   final String section,
                                                   final int sectionPosition,
                                                   final Project project) {
      myPsiElements = psiElements;
      mySection = section;
      mySectionPosition = sectionPosition;
      myProject = project;
    }

    @Override
    public boolean checkInput(final String inputString) {
      return inputString.indexOf('.') < 0;
    }

    @Override
    public boolean canClose(final String inputString) {
      RenameProcessor renameProcessor = null;
      for (final PsiElement psiElement : myPsiElements) {
        assert psiElement instanceof PsiNamedElement;
        final String oldName = ((PsiNamedElement)psiElement).getName();
        assert oldName != null;
        final String newName =
          oldName.substring(0, mySectionPosition) + inputString + oldName.substring(mySectionPosition + mySection.length());
        if (renameProcessor == null) {
          renameProcessor = new RenameProcessor(myProject, psiElement, newName, false, false);
        }
        else {
          renameProcessor.addElement(psiElement, newName);
        }
      }
      assert renameProcessor != null;
      renameProcessor.setCommandName(PropertiesBundle.message("rename.resource.bundle.section.dialog.title"));
      renameProcessor.doRun();
      return true;
    }
  }

  private static class ResourceBundleBaseNameInputValidator implements InputValidator {
    private final Project myProject;
    private final ResourceBundle myResourceBundle;

    public ResourceBundleBaseNameInputValidator(final Project project, final ResourceBundle resourceBundle) {
      myProject = project;
      myResourceBundle = resourceBundle;
    }

    public boolean checkInput(String inputString) {
      return inputString.indexOf(File.separatorChar) < 0 && inputString.indexOf('/') < 0;
    }

    public boolean canClose(final String inputString) {
      final List<PropertiesFile> propertiesFiles = myResourceBundle.getPropertiesFiles();
      for (PropertiesFile propertiesFile : propertiesFiles) {
        if (!FileModificationService.getInstance().prepareFileForWrite(propertiesFile.getContainingFile())) return false;
      }

      RenameProcessor renameProcessor = null;
      final String baseName = myResourceBundle.getBaseName();
      for (PropertiesFile propertiesFile : propertiesFiles) {
        final VirtualFile virtualFile = propertiesFile.getVirtualFile();
        if (virtualFile == null) {
          continue;
        }
        final String newName =
          inputString + virtualFile.getNameWithoutExtension().substring(baseName.length()) + "." + virtualFile.getExtension();
        if (renameProcessor == null) {
          renameProcessor = new RenameProcessor(myProject, propertiesFile.getContainingFile(), newName, false, false);
          continue;
        }
        renameProcessor.addElement(propertiesFile.getContainingFile(), newName);
      }
      if (renameProcessor == null) {
        LOG.assertTrue(false);
        return true;
      }

      renameProcessor.setCommandName(PropertiesBundle.message("rename.resource.bundle.dialog.title"));
      renameProcessor.doRun();

      return true;
    }
  }
}
