/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.yaml.meta.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementTypes;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.meta.model.Field;
import org.jetbrains.yaml.meta.model.YamlMetaClass;
import org.jetbrains.yaml.meta.model.YamlMetaType;
import org.jetbrains.yaml.meta.model.YamlScalarType;
import org.jetbrains.yaml.psi.*;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER_TRIMMED;

@ApiStatus.Experimental
public abstract class YamlMetaTypeCompletionProviderBase extends CompletionProvider<CompletionParameters> {
  protected static final Logger LOG = Logger.getInstance(YamlMetaTypeCompletionProviderBase.class);

  @Nullable
  protected abstract YamlMetaTypeProvider getMetaTypeProvider(@NotNull CompletionParameters params);

  @Override
  protected void addCompletions(@NotNull CompletionParameters params, ProcessingContext context, @NotNull CompletionResultSet result) {
    final YamlMetaTypeProvider metaTypeProvider = getMetaTypeProvider(params);
    if (metaTypeProvider == null) {
      return;
    }

    PsiElement position = params.getPosition();

    if (!isOfType(position.getParent(), YAMLElementTypes.SCALAR_PLAIN_VALUE)) {
      //weird, should be filtered by contributor
      return;
    }

    YAMLScalar insertedScalar = (YAMLScalar)position.getParent();

    /*
    trace("Position: " + getDebugInfo(position));
    trace("Position parent: " + getDebugInfo(insertedScalar));
    */

    if (insertedScalar.getTextRange().getStartOffset() < params.getOffset()) {
      // inserting scalar just after the end of `key:` transforms the key into scalar `key:IntelliJIdeaRulezzz`,
      // see valueMisplaced tree sample test
      int positionOffset = params.getOffset() - insertedScalar.getTextRange().getStartOffset();
      assert positionOffset > 0;
      String combinedText = insertedScalar.getText();
      if (positionOffset >= combinedText.length()) {
        //weird, just stop here since we don't understand how could it happen
        return;
      }
      if (combinedText.charAt(positionOffset - 1) == ':') {
        trace("Completion rejected: misplaced just after key position : " + YamlDebugUtil.getDebugInfo(position));
        return;
      }
    }

    YamlMetaTypeProvider.MetaTypeProxy meta = metaTypeProvider.getMetaTypeProxy(insertedScalar);
    trace("meta: " + meta);
    if (meta == null) {
      return;
    }
    YamlMetaType metaType = meta.getMetaType();
    if (metaType instanceof YamlScalarType) {
      YamlScalarType scalarType = (YamlScalarType)metaType;
      if (insertedScalar.getParent() instanceof YAMLKeyValue) {
        PsiElement prevSibling = PsiTreeUtil.skipWhitespacesBackward(insertedScalar);
        if (isOfType(prevSibling, YAMLTokenTypes.SCALAR_KEY)) {
          addValueCompletions(insertedScalar, scalarType, result, Collections.emptyMap());
          return;
        }
      }

      if (insertedScalar.getParent() instanceof YAMLSequenceItem) {
        YAMLSequenceItem currentItem = (YAMLSequenceItem)insertedScalar.getParent();

        List<YAMLSequenceItem> siblingItems = Optional.ofNullable(currentItem.getParent())
          .filter(YAMLSequence.class::isInstance)
          .map(YAMLSequence.class::cast)
          .map(YAMLSequence::getItems)
          .orElse(Collections.emptyList());

        Map<String, YAMLScalar> siblingValues =
          siblingItems.stream()
            .filter(i -> i.getKeysValues().isEmpty()) // we only need are interested in literal siblings
            .filter(i -> !currentItem.equals(i))
            .map(YAMLSequenceItem::getValue)
            .filter(Objects::nonNull)
            .filter(YAMLScalar.class::isInstance)
            .map(YAMLScalar.class::cast)
            .collect(Collectors.toMap(scalar -> scalar.getText().trim(), scalar -> scalar));

        addValueCompletions(insertedScalar, scalarType, result, siblingValues);
        return;
      }
    }
    if (metaType instanceof YamlMetaClass) {
      addKeyCompletions(params, metaTypeProvider, meta, result, insertedScalar);
    }
  }

  private static void addKeyCompletions(@NotNull CompletionParameters params,
                                        @NotNull YamlMetaTypeProvider metaTypeProvider,
                                        @NotNull YamlMetaTypeProvider.MetaTypeProxy meta,
                                        @NotNull CompletionResultSet result,
                                        @NotNull PsiElement insertedScalar) {
    if (!(meta.getMetaType() instanceof YamlMetaClass)) {
      return;
    }
    YamlMetaClass metaClass = (YamlMetaClass)meta.getMetaType();

    YAMLValue metaOwner = metaTypeProvider.getMetaOwner(insertedScalar);
    if (metaOwner instanceof YAMLScalar && metaOwner.getParent() instanceof YAMLMapping) {
      // workaround for case when completion has added scalar in the middle of the tags
      // in the correct YAML mappings should not directly contain scalars, so it is supposedly safe (?)
      metaOwner = (YAMLValue)metaOwner.getParent();
    }
    Collection<YAMLKeyValue> existingPairs = Optional.ofNullable(metaOwner)
      .filter(YAMLMapping.class::isInstance)
      .map(YAMLMapping.class::cast)
      .map(YAMLMapping::getKeyValues)
      .orElse(Collections.emptyList());

    Map<String, YAMLKeyValue> existingByKey = existingPairs.stream().collect(
      Collectors.toMap(kv -> kv.getKeyText().trim(), kv -> kv, (oldValue, newValue) -> oldValue));

    final List<Field> fieldList = metaClass.getFeatures().stream()
      .filter(childField -> !existingByKey.containsKey(childField.getName()) && childField.isEditable())
      .collect(Collectors.toList());

    final boolean needsSequenceItemMark = existingPairs.isEmpty() && needsSequenceItem(meta.getField());

    if (params.getCompletionType() == CompletionType.SMART) {
      final String text = insertedScalar.getText();
      final int caretPos = text.indexOf(DUMMY_IDENTIFIER_TRIMMED);
      @SuppressWarnings("StringToUpperCaseOrToLowerCaseWithoutLocale") final String pattern =
        (caretPos >= 0 ? text.substring(0, caretPos) : text).toLowerCase();

      final Collection<List<Field>> paths = collectPaths(fieldList, pattern.length() > 0 ? 10 : 1);

      for (List<Field> pathToInsert : paths) {
        final Field lastField = pathToInsert.get(pathToInsert.size() - 1);
        //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale
        if (lastField.getName().toLowerCase().startsWith(pattern)) {
          final YamlMetaType.ForcedCompletionPath completionPath = YamlMetaType.ForcedCompletionPath.forDeepCompletion(pathToInsert);
          LookupElementBuilder l = LookupElementBuilder
            .create(completionPath, completionPath.getName())
            .withInsertHandler(new YamlKeyInsertHandlerImpl(needsSequenceItemMark, pathToInsert.get(0)))
            .withTypeText(lastField.getDefaultType().getDisplayName(), getLookupIcon(lastField), true)
            .withStrikeoutness(lastField.isDeprecated());
          result.addElement(l);
        }
      }
    }
    else {
      fieldList.stream()
        .filter(childField -> !existingByKey.containsKey(childField.getName()))
        .forEach(childField -> registerBasicKeyCompletion(childField, result, insertedScalar, needsSequenceItemMark));
    }
  }

  private static boolean needsSequenceItem(@NotNull Field parentField) {
    return parentField.isMany() &&
           !parentField.hasRelationSpecificType(Field.Relation.OBJECT_CONTENTS);
  }

  private static void registerBasicKeyCompletion(@NotNull Field toBeInserted,
                                                 @NotNull CompletionResultSet result,
                                                 @NotNull PsiElement insertedScalar,
                                                 boolean needsSequenceItemMark) {
    List<LookupElementBuilder> lookups = toBeInserted.getKeyLookups(insertedScalar);
    if (!lookups.isEmpty()) {
      InsertHandler<LookupElement> keyInsertHandler = new YamlKeyInsertHandlerImpl(needsSequenceItemMark, toBeInserted);
      lookups.stream()
        .map(l -> l.withInsertHandler(keyInsertHandler))
        .forEach(result::addElement);
    }
  }

  @Nullable
  private static Icon getLookupIcon(@NotNull final Field field) {
    if (field.isMany()) {
      return AllIcons.Json.Array;
    }
    return null;
  }

  @NotNull
  private static Collection<List<Field>> collectPaths(@NotNull final Collection<Field> fields, final int deepness) {
    Collection<List<Field>> result = new ArrayList<>();
    doCollectPathsRec(fields, Collections.emptyList(), result, deepness);
    return result;
  }

  private static void doCollectPathsRec(@NotNull final Collection<Field> fields,
                                        @NotNull final List<Field> currentPath,
                                        @NotNull final Collection<List<Field>> result, final int deepness) {
    if (currentPath.size() >= deepness) {
      return;
    }

    fields.forEach(field -> {
      final List<Field> fieldPath = Stream.concat(currentPath.stream(), Stream.of(field)).collect(Collectors.toList());
      result.add(fieldPath);
      final YamlMetaType metaType = field.getType(field.getDefaultRelation());
      if (metaType instanceof YamlMetaClass && !field.isAnyNameAllowed()) {
        doCollectPathsRec(((YamlMetaClass)metaType).getFeatures().stream().filter(Field::isEditable).collect(Collectors.toList()),
                          fieldPath, result, deepness);
      }
    });
  }

  private static void addValueCompletions(@NotNull YAMLScalar insertedScalar, @NotNull YamlScalarType meta,
                                          @NotNull CompletionResultSet result, @NotNull Map<String, YAMLScalar> siblings) {
    meta.getValueLookups(insertedScalar).stream()
      .filter(lookup -> !siblings.containsKey(lookup.getLookupString()))
      .forEach(result::addElement);
  }

  private static void trace(String text) {
    LOG.trace(text);
    //System.err.println(getClass().getSimpleName() + ":" + text);
  }

  private static boolean isOfType(@Nullable PsiElement psi, @NotNull IElementType type) {
    return psi != null && psi.getNode().getElementType() == type;
  }
}
