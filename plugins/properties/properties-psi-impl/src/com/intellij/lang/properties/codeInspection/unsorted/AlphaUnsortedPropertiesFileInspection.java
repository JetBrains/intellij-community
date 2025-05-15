// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.codeInspection.unsorted;

import com.intellij.codeInspection.*;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.PropertiesList;
import com.intellij.lang.properties.psi.PropertyKeyValueFormat;
import com.intellij.lang.properties.psi.codeStyle.PropertiesCodeStyleSettings;
import com.intellij.lang.properties.psi.impl.PropertiesFileImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class AlphaUnsortedPropertiesFileInspection extends LocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(AlphaUnsortedPropertiesFileInspection.class);

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @Override
      public void visitFile(@NotNull PsiFile psiFile) {
        var propertiesFile = findPropertiesFile(psiFile);
        if (propertiesFile == null) return;
        var resourceBundle = propertiesFile.getResourceBundle();
        final String resourceBundleBaseName = resourceBundle.getBaseName();
        if (!isResourceBundleAlphaSortedExceptOneFile(resourceBundle, propertiesFile)) {
          holder.registerProblem(psiFile,
                                 PropertiesBundle.message("inspection.alpha.unsorted.properties.file.description1", resourceBundleBaseName),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                 new PropertiesSorterQuickFix(false));
          return;
        }
        if (!propertiesFile.isAlphaSorted()) {
          PropertiesSorterQuickFix fix = new PropertiesSorterQuickFix(true);
          holder.registerProblem(psiFile, PropertiesBundle.message("inspection.alpha.unsorted.properties.file.description"),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fix);
        }
      }
    };
  }

  private static @Nullable PropertiesFile findPropertiesFile(@NotNull PsiFile file) {
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(file);
    if (!(propertiesFile instanceof PropertiesFileImpl)) {
      return null;
    }
    for (AlphaUnsortedPropertiesFileInspectionSuppressor filter : AlphaUnsortedPropertiesFileInspectionSuppressor.EP_NAME.getExtensions()) {
      if (filter.suppressInspectionFor(propertiesFile)) {
        return null;
      }
    }
    return propertiesFile;
  }

  private static boolean isResourceBundleAlphaSortedExceptOneFile(final @NotNull ResourceBundle resourceBundle,
                                                                  final @NotNull PropertiesFile exceptedFile) {
    for (PropertiesFile file : resourceBundle.getPropertiesFiles()) {
      if (!(file instanceof PropertiesFileImpl)) {
        return true;
      }
      if (!file.equals(exceptedFile) && !file.isAlphaSorted()) {
        return false;
      }
    }
    return true;
  }

  private static final class PropertiesSorterQuickFix implements LocalQuickFix {
    private final boolean myOnlyCurrentFile;

    private PropertiesSorterQuickFix(boolean onlyCurrentFile) {
      myOnlyCurrentFile = onlyCurrentFile;
    }

    @Override
    public @NotNull String getFamilyName() {
      return PropertiesBundle.message("properties.sorter.quick.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      Collection<PropertiesFile> filesToSort = getFilesToSort(descriptor.getPsiElement().getContainingFile());
      final boolean force = filesToSort.size() == 1;
      for (PropertiesFile file : filesToSort) {
        if (!force && file.isAlphaSorted()) {
          continue;
        }
        sortPropertiesFile(file);
      }
    }

    private @NotNull Collection<PropertiesFile> getFilesToSort(@NotNull PsiFile file) {
      PropertiesFile propertiesFile = findPropertiesFile(file);
      if (propertiesFile == null) return Collections.emptyList();
      if (myOnlyCurrentFile) {
        return Collections.singleton(propertiesFile);
      }
      return propertiesFile.getResourceBundle().getPropertiesFiles();
    }
  }

  private static void sortPropertiesFile(final PropertiesFile file) {
    final List<IProperty> properties = new ArrayList<>(file.getProperties());

    properties.sort(Comparator.comparing(IProperty::getKey, String.CASE_INSENSITIVE_ORDER));

    final PropertiesList propertiesList = PsiTreeUtil.findChildOfType(file.getContainingFile(), PropertiesList.class);
    if (propertiesList == null) return;

    final char delimiter = PropertiesCodeStyleSettings.getInstance(file.getProject()).getDelimiter();
    final StringBuilder rawText = new StringBuilder(propertiesList.getDocCommentText());
    for (int i = 0; i < properties.size(); i++) {
      IProperty property = properties.get(i);
      final String value = property.getValue();
      final String commentAboveProperty = property.getDocCommentText();
      if (commentAboveProperty != null) {
        rawText.append(commentAboveProperty);
      }
      final String key = property.getKey();
      final String propertyText;
      if (key != null) {
        propertyText =
          PropertiesElementFactory.getPropertyText(key, value != null ? value : "", delimiter, null, PropertyKeyValueFormat.FILE);
        rawText.append(propertyText);
        if (i != properties.size() - 1) {
          rawText.append("\n");
        }
      }
    }

    final PropertiesFile fakeFile = PropertiesElementFactory.createPropertiesFile(file.getProject(), rawText.toString());

    final PropertiesList fakePropertiesList = PsiTreeUtil.findChildOfType(fakeFile.getContainingFile(), PropertiesList.class);
    LOG.assertTrue(fakePropertiesList != null);

    propertiesList.replace(fakePropertiesList);
  }

  @Override
  public @NotNull String getShortName() {
    return "AlphaUnsortedPropertiesFile";
  }
}
