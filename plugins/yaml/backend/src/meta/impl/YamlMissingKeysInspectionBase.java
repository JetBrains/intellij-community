// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.meta.impl;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.YAMLElementGenerator;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.meta.model.YamlMetaType;
import org.jetbrains.yaml.meta.model.YamlScalarType;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLSequenceItem;

import java.util.*;
import java.util.stream.Collectors;

@ApiStatus.Internal
public abstract class YamlMissingKeysInspectionBase extends YamlMetaTypeInspectionBase {

  @Override
  protected @NotNull PsiElementVisitor doBuildVisitor(@NotNull ProblemsHolder holder, @NotNull YamlMetaTypeProvider metaTypeProvider) {
    return new StructureChecker(holder, metaTypeProvider);
  }

  protected class StructureChecker extends SimpleYamlPsiVisitor {
    private final YamlMetaTypeProvider myMetaTypeProvider;
    private final ProblemsHolder myProblemsHolder;

    public StructureChecker(@NotNull ProblemsHolder problemsHolder, @NotNull YamlMetaTypeProvider metaTypeProvider) {
      myProblemsHolder = problemsHolder;
      myMetaTypeProvider = metaTypeProvider;
    }

    @Override
    protected void visitYAMLMapping(@NotNull YAMLMapping mapping) {
      final YamlMetaTypeProvider.MetaTypeProxy meta = myMetaTypeProvider.getMetaTypeProxy(mapping);

      if (meta == null) {
        return;
      }

      final YamlMetaType metaType = meta.getMetaType();
      if (metaType instanceof YamlScalarType) {
        return;
      }

      final Collection<String> missingKeys = getMissingKeys(mapping, metaType);
      if (!missingKeys.isEmpty()) {
        String msg = YAMLBundle.message("YamlMissingKeysInspectionBase.missing.keys", composeKeyList(missingKeys));
        PsiElement element = getElementToHighlight(mapping);
        List<LocalQuickFix> quickFixes = getQuickFixes(missingKeys, element);
        myProblemsHolder.registerProblem(
          element, msg, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY)
        );
      }
    }

    protected @NotNull List<LocalQuickFix> getQuickFixes(@NotNull Collection<String> missingKeys, @NotNull PsiElement element) {
      return List.of(new AddMissingKeysQuickFix(missingKeys, element));
    }
  }

  private static class AddMissingKeysQuickFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    @SafeFieldForPreview private final Collection<String> myMissingKeys;

    AddMissingKeysQuickFix(final @NotNull Collection<String> missingKeys, final @NotNull PsiElement psiElement) {
      super(psiElement);
      myMissingKeys = missingKeys;
    }

    @Override
    public @Nls @NotNull String getText() {
      return getFamilyName();
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return YAMLBundle.message("YamlMissingKeysInspectionBase.add.missing.keys.quickfix.name");
    }

    @Override
    public boolean isAvailable(@NotNull Project project,
                               @NotNull PsiFile psiFile,
                               @Nullable Editor editor,
                               @NotNull PsiElement startElement,
                               @NotNull PsiElement endElement) {
      PsiElement mapping = getMappingFromHighlightElement(startElement);
      return mapping != null;
    }

    @Override
    public void invoke(@NotNull Project project,
                       @NotNull PsiFile psiFile,
                       @Nullable Editor editor,
                       @NotNull PsiElement startElement,
                       @NotNull PsiElement endElement) {
      PsiElement mapping = getMappingFromHighlightElement(startElement);
      if (mapping == null) {
        return;
      }

      final YAMLElementGenerator elementGenerator = YAMLElementGenerator.getInstance(project);
      int indent = YAMLUtil.getIndentToThisElement(mapping);

      PsiElement firstInsertedKey = null;
      for (final String missingKey : myMissingKeys) {
        mapping.add(elementGenerator.createEol());
        mapping.add(elementGenerator.createIndent(indent));
        PsiElement newElement = mapping.add(elementGenerator.createYamlKeyValue(missingKey, ""));
        if (firstInsertedKey == null) {
          firstInsertedKey = newElement;
        }
      }

      if (editor != null && firstInsertedKey != null) {
        editor.getCaretModel().moveToOffset(firstInsertedKey.getTextOffset() + firstInsertedKey.getTextLength());
      }
    }
  }

  private static @Nullable PsiElement getMappingFromHighlightElement(PsiElement elementToHighlight) {
    if (elementToHighlight instanceof YAMLDocument) {
      return PsiTreeUtil.getChildOfAnyType(elementToHighlight, YAMLMapping.class);
    }

    PsiElement parent = elementToHighlight.getParent();
    if (parent instanceof YAMLKeyValue) {
      return ((YAMLKeyValue)parent).getValue();
    }
    else if (parent instanceof YAMLSequenceItem) {
      return ((YAMLSequenceItem)parent).getValue();
    }
    else {
      return PsiTreeUtil.getParentOfType(elementToHighlight, YAMLMapping.class);
    }
  }

  protected @NotNull PsiElement getElementToHighlight(@NotNull YAMLMapping mapping) {
    final PsiElement parent = mapping.getParent();
    if (parent instanceof YAMLDocument) {
      return parent;
    }
    else if (parent instanceof YAMLSequenceItem) {
      final PsiElement key = parent.getFirstChild();
      return key != null ? key : mapping;
    }
    else if (parent instanceof YAMLKeyValue) {
      final PsiElement key = ((YAMLKeyValue)parent).getKey();
      return key != null ? key : mapping;
    }
    else {
      return mapping;
    }
  }

  private static @NotNull String composeKeyList(final @NotNull Collection<String> missingKeys) {
    return String.join(", ", missingKeys);
  }

  private static @NotNull Collection<String> getMissingKeys(@NotNull YAMLMapping mapping, @NotNull YamlMetaType metaClass) {
    Set<String> existingKeys = mapping.getKeyValues().stream().map(it -> it.getKeyText().trim()).collect(Collectors.toSet());
    return metaClass.computeMissingFields(existingKeys);
  }
}
