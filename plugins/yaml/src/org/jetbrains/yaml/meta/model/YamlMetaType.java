// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.meta.model;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.*;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.*;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApiStatus.Internal
public abstract class YamlMetaType {
  @NotNull
  private final String myTypeName;
  @NotNull
  private String myDisplayName;

  protected YamlMetaType(@NonNls @NotNull String typeName) {
    myTypeName = typeName;
    myDisplayName = typeName;
  }

  @NotNull
  @Contract(pure = true)
  public final String getTypeName() {
    return myTypeName;
  }

  @NotNull
  @Contract(pure = true)
  public String getDisplayName() {
    return myDisplayName;
  }

  @NotNull
  @Contract(pure = true)
  public Icon getIcon() {
    return AllIcons.Json.Object;
  }

  public void setDisplayName(@NonNls @NotNull final String displayName) {
    myDisplayName = displayName;
  }

  @Nullable
  public abstract Field findFeatureByName(@NotNull String name);

  /**
   * Computes the set of {@link Field#getName()}s which are missing in the given set of the existing keys.
   *
   * @see org.jetbrains.yaml.meta.impl.YamlMissingKeysInspectionBase
   */
  @NotNull
  public abstract List<String> computeMissingFields(@NotNull Set<String> existingFields);

  /**
   * Computes the list of fields that should be included into the completion list for the key completion inside the given mapping,
   * which is guaranteed to be typed by <code>this<code/> type.
   * <p/>
   * It is assumed that the list does not depend on the insertion position inside the <code>existingMapping</code>.
   * As an optimisation, the result list may include fields which are already present in the <code>existingMapping</code>, the additional
   * filtering will be done by the caller.
   *
   * @see org.jetbrains.yaml.meta.impl.YamlMetaTypeCompletionProviderBase
   */
  @NotNull
  public abstract List<Field> computeKeyCompletions(@Nullable YAMLMapping existingMapping);

  public void validateKey(@NotNull YAMLKeyValue keyValue, @NotNull ProblemsHolder problemsHolder) {
    //
  }

  public void validateValue(@NotNull YAMLValue value, @NotNull ProblemsHolder problemsHolder) {
    //
  }

  /**
   * Validates the value not only at current level but also goes recursively through its children if it's a compound YAML value
   * TODO: unfinished experimental feature to support JSON Schema like features (anyOf, oneOf, allOf, not). Used in Kubernetes plugin. WIP
   */
  public void validateDeep(@NotNull YAMLValue value, @NotNull ProblemsHolder problemsHolder) {
    validateValue(value, problemsHolder);

    // TODO a case for sequence

    if(value instanceof YAMLMapping) {
      YAMLMapping mapping = (YAMLMapping)value;

      Collection<YAMLKeyValue> keyValues = mapping.getKeyValues();

      // check for missing keys
      // TODO reuse with YamlMissingKeysInspectionBase
      final Collection<String> missingKeys =
        computeMissingFields(keyValues.stream().map(it -> it.getKeyText().trim()).collect(Collectors.toSet()));

      if (!missingKeys.isEmpty()) {
        String msg = YAMLBundle.message("YamlMissingKeysInspectionBase.missing.keys", String.join(", ", missingKeys));
        problemsHolder.registerProblem(mapping, msg, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      }

      for (YAMLKeyValue keyValue : keyValues) {
        String featureName = keyValue.getKeyText().trim();

        if(featureName.isEmpty())
          continue;

        Field feature = findFeatureByName(featureName);
        if(feature == null) {
          String msg = YAMLBundle.message("YamlUnknownKeysInspectionBase.unknown.key", keyValue.getKeyText());
          final PsiElement key = keyValue.getKey();
          assert key != null;
          problemsHolder.registerProblem(key, msg, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
          continue;
        }

        YAMLValue subValue = keyValue.getValue();

        if (subValue == null) {
          if (!feature.isEmptyValueAllowed()) {
            // TODO report problem
          }

          continue;
        }

        final Field.Relation relation;
        if(subValue instanceof YAMLScalar) {
          relation = Field.Relation.SCALAR_VALUE;
        } else if (subValue instanceof YAMLSequence) {
          relation = Field.Relation.SEQUENCE_ITEM;
        } else {
          relation = Field.Relation.OBJECT_CONTENTS;
        }

        YamlMetaType subType = feature.getType(relation);

        if(!(subValue instanceof YAMLSequence))
          subType.validateDeep(subValue, problemsHolder);
        else {
          List<YAMLSequenceItem> sequenceItems = ((YAMLSequence)subValue).getItems();
          for (YAMLSequenceItem item : sequenceItems) {
            YAMLValue itemValue = item.getValue();

            if (itemValue == null) {
              if (!feature.isEmptyValueAllowed()) {
                // TODO report problem
              }

              continue;
            }

            subType.validateDeep(itemValue, problemsHolder);
          }
        }
      }
    }
  }


  @NotNull
  public List<? extends LookupElement> getValueLookups(@NotNull YAMLScalar insertedScalar, @Nullable CompletionContext completionContext) {
    return Collections.emptyList();
  }

  /**
   * Builds the insertion markup after the feature name, that is, the part starting from ":".
   * <p>
   * E.g for an integer feature with default value, the insertion suffix markup may look like ": 42&lt;crlf&gt;></>", representing the
   * fill insertion markup of "theAnswer: 42&lt;crlf&gt;"
   */
  public abstract void buildInsertionSuffixMarkup(@NotNull YamlInsertionMarkup markup,
                                                  @NotNull Field.Relation relation,
                                                  @NotNull ForcedCompletionPath.Iteration iteration);

  protected static void buildCompleteKeyMarkup(@NotNull YamlInsertionMarkup markup,
                                               @NotNull Field feature,
                                               @NotNull ForcedCompletionPath.Iteration iteration) {
    markup.append(feature.getName());
    Field.Relation defaultRelation = feature.getDefaultRelation();
    YamlMetaType defaultType = feature.getType(defaultRelation);
    defaultType.buildInsertionSuffixMarkup(markup, defaultRelation, iteration);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + ":" + myTypeName + "@" + Integer.toHexString(hashCode());
  }

  @ApiStatus.Internal
  public static class YamlInsertionMarkup {
    public static final String CRLF_MARKUP = "<crlf>";
    public static final String CARET_MARKUP = "<caret>";
    public static final String SEQUENCE_ITEM_MARKUP = "- ";

    private final StringBuilder myOutput = new StringBuilder();
    private final String myTabSymbol;
    private int myLevel;
    private boolean myCaretAppended;

    public YamlInsertionMarkup() {
      this("  ");
    }

    public YamlInsertionMarkup(@NotNull String tabSymbol) {
      myTabSymbol = tabSymbol;
    }

    public void append(@NotNull String text) {
      myOutput.append(text);
    }

    public void newLineAndTabs() {
      newLineAndTabs(false);
    }

    public void newLineAndTabs(boolean withSequenceItemMark) {
      if (withSequenceItemMark) {
        assert myLevel > 0;
      }

      append(CRLF_MARKUP);
      if (withSequenceItemMark) {
        append(tabs(myLevel - 1));
        append(SEQUENCE_ITEM_MARKUP);
      }
      else {
        append(tabs(myLevel));
      }
    }

    public void appendCaret() {
      if (!myCaretAppended) {
        append(CARET_MARKUP);
      }
      myCaretAppended = true;
    }

    public String getMarkup() {
      return myOutput.toString();
    }

    public void increaseTabs(final int indent) {
      assert indent > 0;
      myLevel += indent;
    }

    public void doTabbedBlock(final int indent, @NotNull final Runnable doWhenTabbed) {
      increaseTabs(indent);
      try {
        doWhenTabbed.run();
      }
      finally {
        decreaseTabs(indent);
      }
    }

    @NotNull
    public String getTabSymbol() {
      return myTabSymbol;
    }

    public void decreaseTabs(int indent) {
      assert indent <= myLevel;
      myLevel -= indent;
    }

    private String tabs(int level) {
      return StringUtil.repeat(myTabSymbol, level);
    }

    public void insertStringAndCaret(Editor editor, String commonPadding) {
      String insertionMarkup = getMarkup();
      String suffixWithCaret = insertionMarkup.replace(CRLF_MARKUP, "\n" + commonPadding);
      int caretIndex = suffixWithCaret.indexOf(CARET_MARKUP);
      String suffix = suffixWithCaret.replace(CARET_MARKUP, "");

      EditorModificationUtil.insertStringAtCaret(editor, suffix, false, true, caretIndex);
    }
  }

  @ApiStatus.Internal
  public static final class ForcedCompletionPath {
    private static final Iteration OFF_PATH_ITERATION = new OffPathIteration();
    private static final Iteration NULL_ITERATION = new NullIteration();
    private static final ForcedCompletionPath NULL_PATH = new ForcedCompletionPath(null);

    @Nullable
    private final List<Field> myCompletionPath;

    @NotNull
    public static ForcedCompletionPath forDeepCompletion(@NotNull final List<Field> completionPath) {
      return new ForcedCompletionPath(completionPath);
    }

    @NotNull
    public static ForcedCompletionPath nullPath() {
      return NULL_PATH;
    }

    private ForcedCompletionPath(@Nullable final List<Field> completionPath) {
      myCompletionPath = completionPath;
    }

    @NotNull
    public String getName() {
      if (myCompletionPath == null) {
        return "<null>";
      }

      return myCompletionPath.stream().map(Field::getName).collect(Collectors.joining("."));
    }

    @Nullable
    public YamlMetaType getFinalizingType() {
      if (myCompletionPath == null || myCompletionPath.size() < 2) {
        return null;
      }

      return myCompletionPath.get(myCompletionPath.size() - 2).getDefaultType();
    }

    @Nullable
    public Field getFinalizingField() {
      if (myCompletionPath == null || myCompletionPath.isEmpty()) {
        return null;
      }

      return myCompletionPath.get(myCompletionPath.size() - 1);
    }

    public Iteration start() {
      return myCompletionPath != null ? new OnPathIteration(1) : NULL_ITERATION;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + "(" + getName() + ")";
    }

    public interface Iteration {
      boolean isEndOfPathReached();

      boolean isNextOnPath(@NotNull Field field);

      @NotNull
      Iteration nextIterationFor(@NotNull Field field);
    }

    private static class OffPathIteration implements Iteration {
      @Override
      public boolean isNextOnPath(@NotNull Field field) {
        return false;
      }

      @Override
      public boolean isEndOfPathReached() {
        return false;
      }

      @NotNull
      @Override
      public Iteration nextIterationFor(@NotNull Field field) {
        return this;
      }
    }

    private static class NullIteration implements Iteration {
      @Override
      public boolean isNextOnPath(@NotNull Field field) {
        return false;
      }

      @Override
      public boolean isEndOfPathReached() {
        return true;
      }

      @NotNull
      @Override
      public Iteration nextIterationFor(@NotNull Field field) {
        return this;
      }
    }

    private final class OnPathIteration implements Iteration {
      private final int myPosition;

      private OnPathIteration(int position) {
        myPosition = position;
      }

      @Override
      public boolean isNextOnPath(@NotNull Field field) {
        assert myCompletionPath != null;
        return !isEndOfPathReached() && field.equals(myCompletionPath.get(myPosition));
      }

      @Override
      public boolean isEndOfPathReached() {
        assert myCompletionPath != null;
        return myPosition >= myCompletionPath.size();
      }

      @NotNull
      @Override
      public Iteration nextIterationFor(@NotNull Field field) {
        assert myCompletionPath != null;
        return isEndOfPathReached() || field.equals(myCompletionPath.get(myPosition)) ?
               new OnPathIteration(myPosition + 1) :
               OFF_PATH_ITERATION;
      }
    }
  }
}
