// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ThreeState;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.JsonSchemaVariantsTreeBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.psi.*;
import org.jetbrains.yaml.psi.impl.YAMLBlockMappingImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class YamlJsonPsiWalker implements JsonLikePsiWalker {

  public YamlJsonPsiWalker() {
  }

  @Override
  public ThreeState isName(PsiElement element) {
    PsiElement parent = element.getParent();
    if (parent instanceof YAMLDocument || parent instanceof YAMLMapping) {
      return ThreeState.YES;
    }

    if (parent instanceof YAMLKeyValue && isFirstChild(element, parent)) {
      ASTNode prev = element.getNode().getTreePrev();
      return prev.getElementType() == YAMLTokenTypes.INDENT ? ThreeState.YES : ThreeState.NO;
    }

    if (parent instanceof YAMLSequenceItem && isFirstChild(element, parent)) {
      return ThreeState.UNSURE;
    }
    return ThreeState.NO;
  }

  private static boolean isFirstChild(PsiElement element, PsiElement parent) {
    PsiElement[] children = parent.getChildren();
    return children.length != 0 && children[0] == element;
  }

  @Override
  public boolean isPropertyWithValue(@NotNull PsiElement element) {
    return element instanceof YAMLKeyValue && ((YAMLKeyValue)element).getValue() != null;
  }

  @Override
  public boolean isTopJsonElement(@NotNull PsiElement element) {
    return element instanceof YAMLFile || element instanceof YAMLDocument;
  }

  @Override
  public PsiElement goUpToCheckable(@NotNull PsiElement element) {
    PsiElement current = element;
    while (current != null && !(current instanceof PsiFile)) {
      if (current instanceof YAMLValue || current instanceof YAMLKeyValue) {
        return current;
      }
      current = current.getParent();
    }
    return null;
  }

  @Override
  public boolean isNameQuoted() {
    return false;
  }

  @Nullable
  @Override
  public JsonValueAdapter createValueAdapter(@NotNull PsiElement element) {
    return element instanceof YAMLValue ? YamlPropertyAdapter.createValueAdapterByType((YAMLValue)element) : null;
  }

  @Override
  public boolean onlyDoubleQuotesForStringLiterals() {
    return false;
  }

  @Override
  public boolean hasPropertiesBehindAndNoComma(@NotNull PsiElement element) {
    return false;
  }

  @Nullable
  @Override
  public JsonPropertyAdapter getParentPropertyAdapter(@NotNull PsiElement element) {
    final YAMLKeyValue property = PsiTreeUtil.getParentOfType(element, YAMLKeyValue.class, false);
    if (property == null) return null;
    // it is a parent property only if its value contains the current property
    YAMLValue value = property.getValue();
    if (value == null || !PsiTreeUtil.isAncestor(value, element, true)) return null;
    return new YamlPropertyAdapter(property);
  }

  @Override
  public Set<String> getPropertyNamesOfParentObject(@NotNull PsiElement originalPosition, PsiElement computedPosition) {
    YAMLMapping object = PsiTreeUtil.getParentOfType(originalPosition, YAMLMapping.class);
    if (object == null) object = PsiTreeUtil.getParentOfType(computedPosition, YAMLMapping.class);
    if (object == null) return Collections.emptySet();
    return object.getKeyValues().stream().filter(p -> p != null && p.getName() != null)
                 .map(p -> p.getName()).collect(Collectors.toSet());
  }

  @Nullable
  @Override
  public List<JsonSchemaVariantsTreeBuilder.Step> findPosition(@NotNull PsiElement element, boolean forceLastTransition) {
    final List<JsonSchemaVariantsTreeBuilder.Step> steps = new ArrayList<>();
    PsiElement current = element;
    while (!breakCondition(current)) {
      final PsiElement position = current;
      current = current.getParent();
      if (current instanceof YAMLSequence) {
        YAMLSequence array = (YAMLSequence)current;
        final List<YAMLSequenceItem> expressions = array.getItems();
        int idx = -1;
        for (int i = 0; i < expressions.size(); i++) {
          final YAMLSequenceItem value = expressions.get(i);
          if (position.equals(value)) {
            idx = i;
            break;
          }
        }
        if (idx != -1) {
          steps.add(JsonSchemaVariantsTreeBuilder.Step.createArrayElementStep(idx));
        }
      } else if (current instanceof YAMLSequenceItem) {
        // do nothing - handled by the upper condition
      } else if (current instanceof YAMLKeyValue) {
        final String propertyName = StringUtil.notNullize(((YAMLKeyValue)current).getName());
        current = current.getParent();
        if (!(current instanceof YAMLMapping)) return null;//incorrect syntax?
        steps.add(JsonSchemaVariantsTreeBuilder.Step.createPropertyStep(propertyName));
      } else if (current instanceof YAMLMapping && position instanceof YAMLKeyValue) {
        // if either value or not first in the chain - needed for completion variant
        final String propertyName = StringUtil.notNullize(((YAMLKeyValue)position).getName());
        steps.add(JsonSchemaVariantsTreeBuilder.Step.createPropertyStep(propertyName));
      } else if (breakCondition(current)) {
        break;
      } else {
        if (current instanceof YAMLMapping) {
          List<YAMLPsiElement> elements = ((YAMLMapping)current).getYAMLElements();
          if (elements.size() == 0) return null;
          YAMLPsiElement last = elements.get(elements.size() - 1);
          if (last == position) {
            continue;
          }
        }
        return null;//something went wrong
      }
    }
    Collections.reverse(steps);
    return steps;
  }

  private static boolean breakCondition(PsiElement current) {
    return current instanceof PsiFile || current instanceof YAMLDocument ||
           current instanceof YAMLBlockMappingImpl && current.getParent() instanceof YAMLDocument;
  }

  @Override
  public boolean quotesForStringLiterals() {
    return false;
  }

  @Override
  public String getDefaultObjectValue(boolean includeWhitespaces) {
    return includeWhitespaces ? "\n  " : "";
  }

  @Nullable public String defaultObjectValueDescription() { return "start object"; }

  @Override
  public String getDefaultArrayValue(boolean includeWhitespaces) {
    return includeWhitespaces ? "\n  - " : "- ";
  }

  @Nullable public String defaultArrayValueDescription() { return "start array"; }

  @Override
  public boolean invokeEnterBeforeObjectAndArray() {
    return true;
  }

  @Override
  public String getNodeTextForValidation(PsiElement element) {
    String text = element.getText();
    if (!StringUtil.startsWith(text, "!!")) return text;
    // remove tags
    int spaceIndex = text.indexOf(' ');
    return spaceIndex > 0 ? text.substring(spaceIndex + 1) : text;
  }
}
