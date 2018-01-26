/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.yaml.meta.impl;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.YAMLElementGenerator;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.meta.model.Field;
import org.jetbrains.yaml.meta.model.YamlMetaClass;
import org.jetbrains.yaml.meta.model.YamlMetaType;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLSequenceItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@ApiStatus.Experimental
public abstract class YamlMissingKeysInspectionBase extends YamlMetaTypeInspectionBase {

  @Override
  @NotNull
  protected PsiElementVisitor doBuildVisitor(@NotNull ProblemsHolder holder, @NotNull YamlMetaTypeProvider metaTypeProvider) {
    return new StructureChecker(holder, metaTypeProvider);
  }

  private class StructureChecker extends SimpleYamlPsiVisitor {
    private YamlMetaTypeProvider myMetaTypeProvider;
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
      if (!(metaType instanceof YamlMetaClass)) {
        return;
      }

      final Collection<String> missingKeys = getMissingKeys(mapping, (YamlMetaClass)metaType);
      if (!missingKeys.isEmpty()) {
        String msg = YAMLBundle.message("YamlMissingKeysInspectionBase.missing.keys", composeKeyList(missingKeys));
        myProblemsHolder.registerProblem(getElementToHighlight(mapping), msg, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                         new AddMissingKeysQuickFix(missingKeys, mapping));
      }
    }
  }

  private static class AddMissingKeysQuickFix implements LocalQuickFix {
    private final Collection<String> myMissingKeys;
    private final SmartPsiElementPointer<YAMLMapping> myMappingHolder;

    public AddMissingKeysQuickFix(@NotNull final Collection<String> missingKeys, @NotNull final YAMLMapping mapping) {
      myMissingKeys = missingKeys;
      myMappingHolder = SmartPointerManager.getInstance(mapping.getProject()).createSmartPsiElementPointer(mapping);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return YAMLBundle.message("YamlMissingKeysInspectionBase.add.missing.keys.quickfix.name", new Object[]{});
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final YAMLElementGenerator elementGenerator = YAMLElementGenerator.getInstance(project);
      PsiElement mapping = myMappingHolder.getElement();
      if (mapping == null) {
        return;
      }

      for (final String missingKey : myMissingKeys) {
        mapping.add(elementGenerator.createEol());
        mapping.add(elementGenerator.createIndent(YAMLUtil.getIndentToThisElement(mapping)));
        mapping.add(elementGenerator.createYamlKeyValue(missingKey, ""));
      }
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
    return missingKeys.stream().collect(Collectors.joining(", "));
  }

  @NotNull
  private static Collection<String> getMissingKeys(@NotNull YAMLMapping mapping, @NotNull YamlMetaClass metaClass) {
    final ArrayList<String> missingKeys = new ArrayList<>();
    final Set<String> keySet = mapping.getKeyValues().stream().map(it -> it.getKeyText().trim()).collect(Collectors.toSet());
    for (Field feature : metaClass.getFeatures()) {
      if (feature.isRequired() && !keySet.contains(feature.getName())) {
        missingKeys.add(feature.getName());
      }
    }
    return missingKeys;
  }
}
