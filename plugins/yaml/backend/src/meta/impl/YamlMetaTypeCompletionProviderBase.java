// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.meta.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementTypes;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.meta.model.*;
import org.jetbrains.yaml.meta.model.CompletionContext;
import org.jetbrains.yaml.psi.*;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER_TRIMMED;

@ApiStatus.Internal
public abstract class YamlMetaTypeCompletionProviderBase extends CompletionProvider<CompletionParameters> {
  protected static final Logger LOG = Logger.getInstance(YamlMetaTypeCompletionProviderBase.class);

  protected abstract @Nullable YamlMetaTypeProvider getMetaTypeProvider(@NotNull CompletionParameters params);

  @Override
  protected void addCompletions(@NotNull CompletionParameters params,
                                @NotNull ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    final YamlMetaTypeProvider metaTypeProvider = getMetaTypeProvider(params);
    if (metaTypeProvider == null) {
      return;
    }

    PsiElement position = params.getPosition();

    if (isOfType(position.getParent(), YAMLElementTypes.SCALAR_PLAIN_VALUE, YAMLElementTypes.SCALAR_QUOTED_STRING)) {
      // it's a value or an inserted key (no ':' after)
      processValueOrInsertedKey(params, result, metaTypeProvider);
    }
    else if (isOfType(position, YAMLTokenTypes.SCALAR_KEY) && position.getParent() instanceof YAMLKeyValue) {
      // if it's an updated key (followed by ':')
      processUpdatedKey(params, result, metaTypeProvider);
    }
  }

  private void processUpdatedKey(@NotNull CompletionParameters params,
                                 @NotNull CompletionResultSet result,
                                 YamlMetaTypeProvider metaTypeProvider) {
    YamlMetaTypeProvider.MetaTypeProxy meta = metaTypeProvider.getMetaTypeProxy(params.getPosition().getParent());
    if (meta != null) {
      addKeyCompletions(params, metaTypeProvider, meta, result, params.getPosition());
    }
  }

  private void processValueOrInsertedKey(@NotNull CompletionParameters params,
                                         @NotNull CompletionResultSet result,
                                         YamlMetaTypeProvider metaTypeProvider) {
    final YAMLScalar insertedScalar = (YAMLScalar)params.getPosition().getParent();

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
        trace("Completion rejected: misplaced just after key position : " + YamlDebugUtil.getDebugInfo(params.getPosition()));
      }
    }

    YamlMetaTypeProvider.MetaTypeProxy meta = metaTypeProvider.getMetaTypeProxy(insertedScalar);
    trace("meta: " + meta);
    if (meta == null) {
      return;
    }
    YamlMetaType metaType = meta.getMetaType();

    if (insertedScalar.getParent() instanceof YAMLKeyValue) {
      PsiElement prevSibling = PsiTreeUtil.skipWhitespacesBackward(insertedScalar);
      if (isOfType(prevSibling, YAMLTokenTypes.COLON)) {
        prevSibling = PsiTreeUtil.skipWhitespacesBackward(prevSibling);
      }
      if (isOfType(prevSibling, YAMLTokenTypes.SCALAR_KEY)) {
        boolean hadScalarLookups = addValueCompletions(insertedScalar, metaType, result, Collections.emptyMap(), params);
        if (hadScalarLookups) {
          return;
        }
      }
    }

    if (insertedScalar.getParent() instanceof YAMLSequenceItem currentItem) {

      List<YAMLSequenceItem> siblingItems = Optional.ofNullable(currentItem.getParent())
        .filter(YAMLSequence.class::isInstance)
        .map(YAMLSequence.class::cast)
        .map(YAMLSequence::getItems)
        .orElse(Collections.emptyList());

      Map<String, YAMLScalar> siblingValues =
        siblingItems.stream()
          .filter(i -> i.getKeysValues().isEmpty()) // we only are interested in literal siblings
          .filter(i -> !currentItem.equals(i))
          .map(YAMLSequenceItem::getValue)
          .filter(Objects::nonNull)
          .filter(YAMLScalar.class::isInstance)
          .map(YAMLScalar.class::cast)
          .collect(Collectors.toMap(scalar -> scalar.getText().trim(), scalar -> scalar, (oldVal, newVal) -> newVal));

      boolean hadScalarInSequenceLookups = addValueCompletions(insertedScalar, metaType, result, siblingValues, params);
      if (hadScalarInSequenceLookups && !(metaType instanceof YamlAnyOfType)) {
        return;
      }
    }

    if (!(metaType instanceof YamlScalarType)) { // if it's certainly not a value
      addKeyCompletions(params, metaTypeProvider, meta, result, insertedScalar);
    }
  }

  private void addKeyCompletions(@NotNull CompletionParameters params,
                                 @NotNull YamlMetaTypeProvider metaTypeProvider,
                                 @NotNull YamlMetaTypeProvider.MetaTypeProxy meta,
                                 @NotNull CompletionResultSet result,
                                 @NotNull PsiElement insertedScalar) {
    final YamlMetaType metaType = meta.getMetaType();
    if (metaType instanceof YamlScalarType) {
      return;
    }

    YAMLValue metaOwner = metaTypeProvider.getMetaOwner(insertedScalar);
    if (metaOwner instanceof YAMLScalar && metaOwner.getParent() instanceof YAMLMapping) {
      // workaround for case when completion has added scalar in the middle of the tags
      // in the correct YAML mappings should not directly contain scalars, so it is supposedly safe (?)
      metaOwner = (YAMLValue)metaOwner.getParent();
    }

    YAMLMapping existingMapping = ObjectUtils.tryCast(metaOwner, YAMLMapping.class);
    Collection<YAMLKeyValue> existingPairs = Optional.ofNullable(existingMapping)
      .map(YAMLMapping::getKeyValues)
      .orElse(Collections.emptyList());

    Map<String, YAMLKeyValue> existingByKey = existingPairs.stream().collect(
      Collectors.toMap(kv -> kv.getKeyText().trim(), kv -> kv, (oldValue, newValue) -> oldValue));

    final List<Field> suggestedFields = metaType.computeKeyCompletions(existingMapping);
    final List<Field> filteredList = ContainerUtil
      .filter(suggestedFields, childField -> !existingByKey.containsKey(childField.getName()) && childField.isEditable());

    final boolean needsSequenceItemMark = existingPairs.isEmpty() && needsSequenceItem(meta.getField());

    if (params.getCompletionType() == CompletionType.SMART) {
      final String text = insertedScalar.getText();
      final int caretPos = text.indexOf(DUMMY_IDENTIFIER_TRIMMED);
      String pattern = StringUtil.toLowerCase((caretPos >= 0 ? text.substring(0, caretPos) : text));

      final Collection<List<Field>> paths = collectPaths(filteredList, !pattern.isEmpty() ? 10 : 1);

      for (List<Field> pathToInsert : paths) {
        final Field lastField = pathToInsert.get(pathToInsert.size() - 1);
        if (StringUtil.toLowerCase(lastField.getName()).startsWith(pattern)) {
          final YamlMetaType.ForcedCompletionPath completionPath = YamlMetaType.ForcedCompletionPath.forDeepCompletion(pathToInsert);
          LookupElementBuilder l = LookupElementBuilder
            .create(completionPath, completionPath.getName())
            .withIcon(lastField.getLookupIcon())
            .withInsertHandler(createKeyInsertHandler(params.getPosition().getProject(), needsSequenceItemMark, pathToInsert.get(0)))
            .withTypeText(lastField.getDefaultType().getDisplayName(), true)
            .withStrikeoutness(lastField.isDeprecated());
          result.addElement(l);
        }
      }
    }
    else {
      filteredList.stream()
        .filter(childField -> !existingByKey.containsKey(childField.getName()))
        .forEach(childField -> {
          var lookups = ContainerUtil.filter(childField.getKeyLookups(metaType, insertedScalar), l -> {
            return !existingByKey.containsKey(l.getLookupString());
          });

          registerBasicKeyCompletion(result, lookups,
                                     createKeyInsertHandler(params.getPosition().getProject(), needsSequenceItemMark, childField));
        });
    }
  }

  protected @NotNull InsertHandler<LookupElement> createKeyInsertHandler(@Nullable Project project,
                                                                         boolean needsSequenceItemMark,
                                                                         @NotNull Field childField) {
    return new YamlKeyInsertHandlerImpl(needsSequenceItemMark, childField);
  }

  private static boolean needsSequenceItem(@NotNull Field parentField) {
    return parentField.isMany() &&
           !parentField.hasRelationSpecificType(Field.Relation.OBJECT_CONTENTS);
  }

  protected void registerBasicKeyCompletion(@NotNull CompletionResultSet result,
                                            @NotNull List<LookupElementBuilder> lookups,
                                            @NotNull InsertHandler<LookupElement> insertHandler) {
    if (!lookups.isEmpty()) {
      lookups.stream().map(l -> l.withInsertHandler(insertHandler)).forEach(result::addElement);
    }
  }

  private static @NotNull Collection<List<Field>> collectPaths(final @NotNull Collection<? extends Field> fields, final int deepness) {
    Collection<List<Field>> result = new ArrayList<>();
    doCollectPathsRec(fields, Collections.emptyList(), result, deepness);
    return result;
  }

  private static void doCollectPathsRec(final @NotNull Collection<? extends Field> fields,
                                        final @NotNull List<? extends Field> currentPath,
                                        final @NotNull Collection<? super List<Field>> result, final int deepness) {
    if (currentPath.size() >= deepness) {
      return;
    }

    fields.stream()
      .filter(field -> !field.isAnyNameAllowed())
      .forEach(field -> {
        final List<Field> fieldPath = StreamEx.<Field>of(currentPath).append(field).toList();
        result.add(fieldPath);
        final YamlMetaType metaType = field.getType(field.getDefaultRelation());
        if (metaType instanceof YamlMetaClass) {
          doCollectPathsRec(ContainerUtil.filter(((YamlMetaClass)metaType).getFeatures(), Field::isEditable),
                            fieldPath, result, deepness);
        }
      });
  }

  private static boolean addValueCompletions(@NotNull YAMLScalar insertedScalar,
                                             @NotNull YamlMetaType meta,
                                             @NotNull CompletionResultSet result,
                                             @NotNull Map<String, YAMLScalar> siblings,
                                             @NotNull CompletionParameters completionParameters) {
    List<? extends LookupElement> lookups = meta.getValueLookups(insertedScalar, new CompletionContextImpl(completionParameters, result));
    lookups.stream()
      .filter(lookup -> !siblings.containsKey(lookup.getLookupString()))
      .forEach(result::addElement);
    return !lookups.isEmpty();
  }

  private static void trace(String text) {
    LOG.trace(text);
    //System.err.println(getClass().getSimpleName() + ":" + text);
  }

  private static boolean isOfType(@Nullable PsiElement psi, IElementType @NotNull ... types) {
    if (psi == null) return false;
    IElementType actual = psi.getNode().getElementType();
    return ContainerUtil.exists(types, actual::equals);
  }

  private static class CompletionContextImpl implements CompletionContext {
    private final CompletionType myType;
    private final int myInvocationCount;
    private final String myPrefix;
    private final CompletionResultSet myCompletionResultSet;

    CompletionContextImpl(CompletionParameters completionParameters, CompletionResultSet completionResultSet) {
      myType = completionParameters.getCompletionType();
      myInvocationCount = completionParameters.getInvocationCount();
      myPrefix = computeCompletionPrefix(completionParameters);
      myCompletionResultSet = completionResultSet;
    }

    @Override
    public @NotNull CompletionType getCompletionType() {
      return myType;
    }

    @Override
    public int getInvocationCount() {
      return myInvocationCount;
    }

    @Override
    public @NotNull CompletionResultSet getCompletionResultSet() {
      return myCompletionResultSet;
    }

    @Override
    public @NotNull String getCompletionPrefix() {
      return myPrefix;
    }

    private static @NotNull String computeCompletionPrefix(CompletionParameters parameters) {
      PsiElement position = parameters.getPosition();
      String textWithInsertedPart = parameters.getPosition().getText();
      int positionInRange = parameters.getOffset() - position.getTextRange().getStartOffset();
      if (positionInRange < 0) {
        positionInRange = 0;
      }
      else if (positionInRange > textWithInsertedPart.length()) {
        positionInRange = textWithInsertedPart.length();
      }

      boolean startsWithQuote = YAMLElementTypes.SCALAR_QUOTED_STRING.equals(position.getParent().getNode().getElementType());
      return textWithInsertedPart.substring(startsWithQuote ? "'".length() : 0, positionInRange);
    }
  }
}
