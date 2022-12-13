//Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.meta.impl;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInspection.*;
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
import org.jetbrains.yaml.psi.*;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@ApiStatus.Internal
public abstract class YamlMissingKeysInspectionBase extends YamlMetaTypeInspectionBase {

  @Override
  @NotNull
  protected PsiElementVisitor doBuildVisitor(@NotNull ProblemsHolder holder, @NotNull YamlMetaTypeProvider metaTypeProvider) {
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
        myProblemsHolder.registerProblem(element, msg, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                         new AddMissingKeysQuickFix(missingKeys, element));
      }
    }
  }

  private static class AddMissingKeysQuickFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    @SafeFieldForPreview private final Collection<String> myMissingKeys;

    AddMissingKeysQuickFix(@NotNull final Collection<String> missingKeys, @NotNull final PsiElement psiElement) {
      super(psiElement);
      myMissingKeys = missingKeys;
    }

    @Nls
    @NotNull
    @Override
    public String getText() {
      return getFamilyName();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return YAMLBundle.message("YamlMissingKeysInspectionBase.add.missing.keys.quickfix.name");
    }

    @Override
    public boolean isAvailable(@NotNull Project project,
                               @NotNull PsiFile file,
                               @Nullable Editor editor,
                               @NotNull PsiElement startElement,
                               @NotNull PsiElement endElement) {
      PsiElement mapping = getMappingFromHighlightElement(startElement);
      return mapping != null;
    }

    @Override
    public void invoke(@NotNull Project project,
                       @NotNull PsiFile file,
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

  @Nullable
  private static PsiElement getMappingFromHighlightElement(PsiElement elementToHighlight) {
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

  @NotNull
  protected PsiElement getElementToHighlight(@NotNull YAMLMapping mapping) {
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

  @NotNull
  private static String composeKeyList(@NotNull final Collection<String> missingKeys) {
    return String.join(", ", missingKeys);
  }

  @NotNull
  private static Collection<String> getMissingKeys(@NotNull YAMLMapping mapping, @NotNull YamlMetaType metaClass) {
    Set<String> existingKeys = mapping.getKeyValues().stream().map(it -> it.getKeyText().trim()).collect(Collectors.toSet());
    return metaClass.computeMissingFields(existingKeys);
  }
}
