// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.*;
import org.jetbrains.yaml.psi.impl.YAMLBlockMappingImpl;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class YAMLUtil {
  @SuppressWarnings("unused") // keep compatibility with external plugins
  public static final FileBasedIndex.InputFilter YAML_INPUT_FILTER = YAMLFileBasedIndexUtil.YAML_INPUT_FILTER;

  private static final TokenSet BLANK_LINE_ELEMENTS = TokenSet.andNot(YAMLElementTypes.BLANK_ELEMENTS, YAMLElementTypes.EOL_ELEMENTS);

  /**
   * This method return flattened key path (consist of ancestors until document).
   * </p>
   * YAML are frequently used in configure files. Access to child keys are preformed by dot separator.
   * <pre>{@code
   *  top:
   *    next:
   *      list:
   *        - needKey: value
   * }</pre>
   * Flattened {@code needKey} is {@code top.next.list[0].needKey}
   */
  @NotNull
  public static String getConfigFullName(@NotNull YAMLPsiElement target) {
    return StringUtil.join(getConfigFullNameParts(target), ".");
  }

  public static @NotNull List<String> getConfigFullNameParts(@NotNull YAMLPsiElement target) {
    SmartList<String> result = new SmartList<>();
    PsiElement element = target;
    while (element != null) {
      String elementIndexSuffix = "";
      if (element instanceof YAMLSequenceItem) {
        elementIndexSuffix = "[" + ((YAMLSequenceItem)element).getItemIndex() + "]";
        element = PsiTreeUtil.getParentOfType(element, YAMLKeyValue.class);
      }

      if (element instanceof YAMLKeyValue) {
        String keyText = ((YAMLKeyValue)element).getKeyText();
        result.add(keyText + elementIndexSuffix);
      }
      element = PsiTreeUtil.getParentOfType(element, YAMLKeyValue.class, YAMLSequenceItem.class);
    }
    return ContainerUtil.reverse(result);
  }


  @NotNull
  public static Collection<YAMLKeyValue> getTopLevelKeys(final YAMLFile file) {
    final YAMLValue topLevelValue = file.getDocuments().get(0).getTopLevelValue();
    if (topLevelValue instanceof YAMLMapping) {
      return ((YAMLMapping)topLevelValue).getKeyValues();
    }
    else {
      return Collections.emptyList();
    }
  }

  @Nullable
  public static YAMLKeyValue getQualifiedKeyInFile(final YAMLFile file, List<String> key) {
    return getQualifiedKeyInDocument(file.getDocuments().get(0), key);
  }

  @Nullable
  public static YAMLKeyValue getQualifiedKeyInDocument(@NotNull YAMLDocument document, @NotNull List<String> key) {
    assert key.size() != 0;

    YAMLMapping mapping = ObjectUtils.tryCast(document.getTopLevelValue(), YAMLMapping.class);
    for (int i = 0; i < key.size(); i++) {
      if (mapping == null) {
        return null;
      }

      YAMLKeyValue keyValue = mapping.getKeyValueByKey(String.join(".", key.subList(i, key.size())));
      if (keyValue != null) {
        return keyValue;
      }

      keyValue = mapping.getKeyValueByKey(key.get(i));
      if (keyValue == null || i + 1 == key.size()) {
        return keyValue;
      }

      mapping = ObjectUtils.tryCast(keyValue.getValue(), YAMLMapping.class);
    }
    throw new IllegalStateException("Should have returned from the loop");
  }

  @Nullable
  public static YAMLKeyValue getQualifiedKeyInFile(final YAMLFile file, String... key) {
    return getQualifiedKeyInFile(file, Arrays.asList(key));
  }

  @Nullable
  public static YAMLKeyValue findKeyInProbablyMapping(@Nullable YAMLValue node, @NotNull String keyText) {
    if (!(node instanceof YAMLMapping)) {
      return null;
    }
    return ((YAMLMapping)node).getKeyValueByKey(keyText);
  }

  @Nullable
  public static Pair<PsiElement, String> getValue(final YAMLFile file, String... key) {
    final YAMLKeyValue record = getQualifiedKeyInFile(file, key);
    if (record != null) {
      final PsiElement psiValue = record.getValue();
      return Pair.create(psiValue, record.getValueText());
    }
    return null;
  }

  //public List<String> getAllKeys(final YAMLFile file){
  //  return getAllKeys(file, ArrayUtil.EMPTY_STRING_ARRAY);
  //}
  //
  //public List<String> getAllKeys(final YAMLFile file, final String[] key){
  //  final YAMLPsiElement record = getQualifiedKeyInFile(file, key);
  //  if (record == null){
  //    return Collections.emptyList();
  //  }
  //  PsiElement psiValue = ((YAMLKeyValue)record).getValue();
  //
  //  final StringBuilder builder = new StringBuilder();
  //  for (String keyPart : key) {
  //    if (builder.length() != 0){
  //      builder.append(".");
  //    }
  //    builder.append(keyPart);
  //  }
  //
  //  final ArrayList<String> list = new ArrayList<String>();
  //
  //  addKeysRec(builder.toString(), psiValue, list);
  //  return list;
  //}

  //private static void addKeysRec(final String prefix, final PsiElement element, final List<String> list) {
  //  if (element instanceof YAMLCompoundValue){
  //    for (YAMLPsiElement child : ((YAMLCompoundValue)element).getYAMLElements()) {
  //      addKeysRec(prefix, child, list);
  //    }
  //  }
  //  if (element instanceof YAMLKeyValue){
  //    final YAMLKeyValue yamlKeyValue = (YAMLKeyValue)element;
  //    final PsiElement psiValue = yamlKeyValue.getValue();
  //    String key = yamlKeyValue.getKeyText();
  //    if (prefix.length() > 0){
  //      key = prefix + "." + key;
  //    }
  //    if (YAMLUtil.isScalarOrEmptyCompoundValue(psiValue)) {
  //      list.add(key);
  //    } else {
  //      addKeysRec(key, psiValue, list);
  //    }
  //  }
  //}

  public YAMLKeyValue createI18nRecord(final YAMLFile file, final String key, final String text) {
    return createI18nRecord(file, key.split("\\."), text);
  }

  @Nullable
  public static YAMLKeyValue createI18nRecord(final YAMLFile file, final String[] key, final String text) {
    final YAMLDocument root = file.getDocuments().get(0);
    assert root != null;
    assert key.length > 0;

    YAMLMapping rootMapping = PsiTreeUtil.findChildOfType(root, YAMLMapping.class);
    if (rootMapping == null) {
      final YAMLFile yamlFile = YAMLElementGenerator.getInstance(file.getProject()).createDummyYamlWithText(key[0] + ":");
      final YAMLMapping mapping = (YAMLMapping) yamlFile.getDocuments().get(0).getTopLevelValue();
      assert mapping != null;
      rootMapping = ((YAMLMapping)root.add(mapping));
    }

    YAMLMapping current = rootMapping;
    final int keyLength = key.length;
    int i;
    for (i = 0; i < keyLength; i++) {
      final YAMLKeyValue existingRec = current.getKeyValueByKey(key[i]);
      if (existingRec != null){
        final YAMLMapping nextMapping = ObjectUtils.tryCast(existingRec.getValue(), YAMLMapping.class);

        if (nextMapping != null) {
          current = nextMapping;
          continue;
        }
      }

      // Calc current key indent

      String indent = StringUtil.repeatSymbol(' ', getIndentInThisLine(current));

      // Generate items
      final StringBuilder builder = new StringBuilder();
      builder.append("---");
      for (int j = i; j < keyLength; j++) {
        builder.append("\n").append(indent);
        builder.append(key[j]).append(":");
        indent += "  ";
      }
      builder.append(" ").append(text);

      // Create dummy mapping
      final YAMLFile fileWithKey = YAMLElementGenerator.getInstance(file.getProject()).createDummyYamlWithText(builder.toString());
      final YAMLMapping dummyMapping = PsiTreeUtil.findChildOfType(fileWithKey.getDocuments().get(0), YAMLMapping.class);
      assert dummyMapping != null && dummyMapping.getKeyValues().size() == 1;

      // Add or replace
      final YAMLKeyValue dummyKeyValue = dummyMapping.getKeyValues().iterator().next();
      current.putKeyValue(dummyKeyValue);

      if (!(dummyKeyValue.getValue() instanceof YAMLMapping)) {
        return dummyKeyValue;
      }
      else {
        current = ((YAMLMapping)dummyKeyValue.getValue());
      }

    }

    // Conflict with existing value
    final StringBuilder builder = new StringBuilder();
    final int top = Math.min(i + 1, keyLength);
    for (int j=0;j<top;j++){
      if (builder.length() > 0){
        builder.append('.');
      }
      builder.append(key[j]);
    }
    throw new IncorrectOperationException(YAMLBundle.message("new.name.conflicts.with", builder.toString()));
  }

  public static PsiElement rename(final YAMLKeyValue element, final String newName) {
    if (newName.equals(element.getName())) {
      throw new IncorrectOperationException(YAMLBundle.message("rename.same.name"));
    }
    final YAMLKeyValue topKeyValue = YAMLElementGenerator.getInstance(element.getProject()).createYamlKeyValue(newName, "Foo");

    final PsiElement key = element.getKey();
    if (key == null || topKeyValue.getKey() == null) {
      throw new IllegalStateException();
    }
    key.replace(topKeyValue.getKey());
    return element;
  }

  public static int getIndentInThisLine(@NotNull final PsiElement elementInLine) {
    PsiElement currentElement = elementInLine;
    while (currentElement != null) {
      final IElementType type = currentElement.getNode().getElementType();
      if (type == YAMLTokenTypes.EOL) {
        return 0;
      }
      if (type == YAMLTokenTypes.INDENT) {
        return currentElement.getTextLength();
      }

      currentElement = PsiTreeUtil.prevLeaf(currentElement);
    }
    return 0;
  }

  public static int getIndentToThisElement(@NotNull PsiElement element) {
    if (element instanceof YAMLBlockMappingImpl) {
      try {
        element = ((YAMLBlockMappingImpl)element).getFirstKeyValue();
      } catch (IllegalStateException e) {
        // Spring Boot plug-in modifies PSI-tree into invalid state
        // This is workaround over EA-133507 IDEA-210113
        if (!e.getMessage().equals(YAMLBlockMappingImpl.EMPTY_MAP_MESSAGE)) {
          throw e;
        }
        else {
          Logger.getInstance(YAMLUtil.class).warn(YAMLBlockMappingImpl.EMPTY_MAP_MESSAGE);
        }
      }
    }
    int offset = element.getTextOffset();

    PsiElement currentElement = element;
    while (currentElement != null) {
      final IElementType type = currentElement.getNode().getElementType();
      if (YAMLElementTypes.EOL_ELEMENTS.contains(type)) {
        return offset - currentElement.getTextOffset() - currentElement.getTextLength();
      }

      currentElement = PsiTreeUtil.prevLeaf(currentElement);
    }
    return offset;
  }

  public static boolean psiAreAtTheSameLine(@NotNull PsiElement psi1, @NotNull PsiElement psi2) {
    PsiElement leaf = firstLeaf(psi1);
    PsiElement lastLeaf = firstLeaf(psi2);
    while (leaf != null) {
      if (PsiUtilCore.getElementType(leaf) == YAMLTokenTypes.EOL) {
        return false;
      }
      if (leaf == lastLeaf) {
        return true;
      }
      leaf = PsiTreeUtil.nextLeaf(leaf);
    }
    // It is a kind of magic, normally we should return from the `while` above
    return false;
  }

  private static @Nullable PsiElement firstLeaf(PsiElement psi1) {
    LeafElement leaf = TreeUtil.findFirstLeaf(psi1.getNode());
    if (leaf != null) {
      return leaf.getPsi();
    }
    else {
      return null;
    }
  }


  /**
   * Deletes surrounding whitespace contextually. First attempts to delete {@link YAMLTokenTypes#COMMENT}s on the same line and
   * {@link YAMLElementTypes#SPACE_ELEMENTS} forward, otherwise it will delete {@link YAMLElementTypes#SPACE_ELEMENTS} backward.
   * <p>
   * This is useful for maintaining consistent formatting.
   * <p>
   * E.g.,
   * <pre>{@code
   * foo:
   *   bar: value1 # Same line comment
   *   # Next line comment
   *   baz: value2
   * }</pre>
   * becomes
   * <pre>{@code
   * foo:
   *   bar: value1 # Next line comment
   *   baz: value2
   * }</pre>
   */
  public static void deleteSurroundingWhitespace(@NotNull final PsiElement element) {
    if (element.getNextSibling() != null) {
      deleteElementsOfType(element::getNextSibling, BLANK_LINE_ELEMENTS);
      deleteElementsOfType(element::getNextSibling, YAMLElementTypes.SPACE_ELEMENTS);
    }
    else {
      deleteElementsOfType(element::getPrevSibling, YAMLElementTypes.SPACE_ELEMENTS);
    }
  }

  private static void deleteElementsOfType(@NotNull final Supplier<? extends PsiElement> element, @NotNull final TokenSet types) {
    while (element.get() != null && types.contains(PsiUtilCore.getElementType(element.get()))) {
      element.get().delete();
    }
  }
}
