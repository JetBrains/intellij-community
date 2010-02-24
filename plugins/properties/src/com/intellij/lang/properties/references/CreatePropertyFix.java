/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.properties.references;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class CreatePropertyFix implements IntentionAction, LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.i18n.I18nizeQuickFix");
  private final PsiElement myElement;
  private final String myKey;
  private final List<PropertiesFile> myPropertiesFiles;

  public static final String NAME = PropertiesBundle.message("create.property.quickfix.text");

  public CreatePropertyFix() {
    this(null, null, null);
  }

  public CreatePropertyFix(PsiElement element, String key, final List<PropertiesFile> propertiesFiles) {
    myElement = element;
    myKey = key;
    myPropertiesFiles = propertiesFiles;
  }

  @NotNull
  public String getName() {
    return NAME;
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement psiElement = descriptor.getPsiElement();
    if (isAvailable(project, null, null)) {
      invoke(project, null, psiElement.getContainingFile());
    }
  }

  @NotNull
  public String getText() {
    return NAME;
  }

  public boolean isAvailable(@NotNull Project project, @Nullable Editor editor, @Nullable PsiFile file) {
    return myElement.isValid();
  }

  public void invoke(@NotNull final Project project, @Nullable Editor editor, @NotNull PsiFile file) {
    invokeAction(project, file, myElement, myKey, myPropertiesFiles);
  }

  @Nullable
  private static Pair<String, String> invokeAction(@NotNull final Project project,
                                                   @NotNull PsiFile file,
                                                   @NotNull PsiElement psiElement,
                                                   @Nullable final String suggestedKey,
                                                   @Nullable final List<PropertiesFile> propertiesFiles) {
    final I18nizeQuickFixModel model;
    final I18nizeQuickFixDialog.DialogCustomization dialogCustomization = createDefaultCustomization(suggestedKey, propertiesFiles);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      model = new I18nizeQuickFixModel() {
        public String getValue() {
          return "";
        }

        public String getKey() {
          return dialogCustomization.getSuggestedName();
        }

        public boolean hasValidData() {
          return true;
        }

        public Collection<PropertiesFile> getAllPropertiesFiles() {
          return propertiesFiles;
        }
      };
    } else {
      model = new I18nizeQuickFixDialog(
        project,
        file,
        NAME, dialogCustomization
      );
    }
    return doAction(project, psiElement, model);
  }

  protected static I18nizeQuickFixDialog.DialogCustomization createDefaultCustomization(String suggestedKey, List<PropertiesFile> propertiesFiles) {
    return new I18nizeQuickFixDialog.DialogCustomization(NAME, false, true, propertiesFiles, suggestedKey == null ? "" : suggestedKey);
  }

  protected static Pair<String, String> doAction(Project project, PsiElement psiElement, 
                                               I18nizeQuickFixModel model) {
    if (!model.hasValidData()) {
      return null;
    }
    final String key = model.getKey();
    final String value = model.getValue();

    final Collection<PropertiesFile> selectedPropertiesFiles = model.getAllPropertiesFiles();
    createProperty(project, psiElement, selectedPropertiesFiles, key, value);

    return new Pair<String, String>(key, value);
  }

  public static void createProperty(@NotNull final Project project,
                                    @NotNull final PsiElement psiElement,
                                    @NotNull final Collection<PropertiesFile> selectedPropertiesFiles,
                                    @NotNull final String key,
                                    @NotNull final String value) {
    for (PropertiesFile selectedFile : selectedPropertiesFiles) {
      if (!CodeInsightUtilBase.prepareFileForWrite(selectedFile)) return;
    }
    UndoUtil.markPsiFileForUndo(psiElement.getContainingFile());

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          public void run() {
            try {
              I18nUtil.createProperty(project, selectedPropertiesFiles, key, value);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }, CodeInsightBundle.message("quickfix.i18n.command.name"), project);
      }
    });
  }

  public boolean startInWriteAction() {
    return false;
  }
}
