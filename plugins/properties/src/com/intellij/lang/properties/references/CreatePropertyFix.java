// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.references;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.PsiAnchor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class CreatePropertyFix implements IntentionAction, LocalQuickFix {
  private static final Logger LOG = Logger.getInstance(CreatePropertyFix.class);
  private final PsiAnchor myElement;
  private final String myKey;
  private final List<PropertiesFile> myPropertiesFiles;

  public CreatePropertyFix() {
    this(null, null, null);
  }

  public CreatePropertyFix(PsiElement element, String key, final List<PropertiesFile> propertiesFiles) {
    myElement = element == null ? null : PsiAnchor.create(element);
    myKey = key;
    myPropertiesFiles = propertiesFiles;
  }

  @Override
  public @NotNull String getName() {
    return getFixName();
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement psiElement = descriptor.getPsiElement();
    if (isAvailable(project, null, null)) {
      invoke(project, null, psiElement.getContainingFile());
    }
  }

  @Override
  public @NotNull String getText() {
    return getFixName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, @Nullable Editor editor, @Nullable PsiFile psiFile) {
    return myElement != null && myElement.retrieve() != null;
  }

  @Override
  public void invoke(final @NotNull Project project, @Nullable Editor editor, @NotNull PsiFile psiFile) {
    invokeAction(project, psiFile, myElement.retrieve(), myKey, myPropertiesFiles);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    String fileName = myPropertiesFiles != null && !myPropertiesFiles.isEmpty() ? myPropertiesFiles.get(0).getName() : "";
    return new IntentionPreviewInfo.CustomDiff(PropertiesFileType.INSTANCE, fileName, myKey + "=", myKey + "=...");
  }

  private void invokeAction(final @NotNull Project project,
                            @NotNull PsiFile file,
                            @NotNull PsiElement psiElement,
                            final @Nullable String suggestedKey,
                            final @Nullable List<PropertiesFile> propertiesFiles) {
    final I18nizeQuickFixModel model;
    final I18nizeQuickFixDialog.DialogCustomization dialogCustomization = createDefaultCustomization(suggestedKey, propertiesFiles);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      model = new I18nizeQuickFixModel() {
        @Override
        public String getValue() {
          return "";
        }

        @Override
        public String getKey() {
          return dialogCustomization.getSuggestedName();
        }

        @Override
        public boolean hasValidData() {
          return true;
        }

        @Override
        public Collection<PropertiesFile> getAllPropertiesFiles() {
          return propertiesFiles;
        }
      };
    } else {
      model = new I18nizeQuickFixDialog(
        project,
        file,
        getFixName(), dialogCustomization
      );
    }
    doAction(project, psiElement, model);
  }

  protected static I18nizeQuickFixDialog.DialogCustomization createDefaultCustomization(String suggestedKey, List<PropertiesFile> propertiesFiles) {
    return new I18nizeQuickFixDialog.DialogCustomization(getFixName(), false, true, propertiesFiles, suggestedKey == null ? "" : suggestedKey);
  }

  protected Couple<String> doAction(Project project, PsiElement psiElement, I18nizeQuickFixModel model) {
    if (!model.hasValidData()) {
      return null;
    }
    final String key = model.getKey();
    final String value = model.getValue();

    final Collection<PropertiesFile> selectedPropertiesFiles = model.getAllPropertiesFiles();
    createProperty(project, psiElement, selectedPropertiesFiles, key, value);

    return Couple.of(key, value);
  }

  public static void createProperty(final @NotNull Project project,
                                    final @NotNull PsiElement psiElement,
                                    final @NotNull Collection<? extends PropertiesFile> selectedPropertiesFiles,
                                    final @NotNull String key,
                                    final @NotNull String value) {
    for (PropertiesFile selectedFile : selectedPropertiesFiles) {
      if (!FileModificationService.getInstance().prepareFileForWrite(selectedFile.getContainingFile())) return;
    }
    UndoUtil.markPsiFileForUndo(psiElement.getContainingFile());

    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(project, () -> {
      try {
        I18nUtil.createProperty(project, selectedPropertiesFiles, key, value);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }, PropertiesBundle.message("quickfix.i18n.command.name"), project));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public static @IntentionName String getFixName() {
    return PropertiesBundle.message("create.property.quickfix.text");
  }
}
