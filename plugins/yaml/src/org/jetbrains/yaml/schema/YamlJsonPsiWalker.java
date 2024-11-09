// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementGenerator;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.*;
import org.jetbrains.yaml.psi.impl.YAMLArrayImpl;
import org.jetbrains.yaml.psi.impl.YAMLBlockMappingImpl;
import org.jetbrains.yaml.psi.impl.YAMLHashImpl;

import java.util.*;
import java.util.stream.Collectors;

public final class YamlJsonPsiWalker implements JsonLikePsiWalker {
  public static final YamlJsonPsiWalker INSTANCE = new YamlJsonPsiWalker();

  private YamlJsonPsiWalker() {
  }

  @Override
  public boolean isQuotedString(@NotNull PsiElement element) {
    return element instanceof YAMLQuotedText;
  }

  @Override
  public ThreeState isName(PsiElement element) {
    PsiElement parent = element.getParent();
    if (parent instanceof YAMLDocument || parent instanceof YAMLMapping) {
      return ThreeState.YES;
    }

    if (parent instanceof YAMLKeyValue && isFirstChild(element, parent)) {
      ASTNode prev = element.getNode().getTreePrev();
      return prev != null && prev.getElementType() == YAMLTokenTypes.INDENT ? ThreeState.YES : ThreeState.NO;
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
  public boolean acceptsEmptyRoot() {
    return true;
  }

  @Override
  public PsiElement findElementToCheck(@NotNull PsiElement element) {
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
  public boolean requiresNameQuotes() {
    return false;
  }

  @Override
  public @Nullable JsonValueAdapter createValueAdapter(@NotNull PsiElement element) {
    if (element instanceof YAMLValue) {
      return YamlPropertyAdapter.createValueAdapterByType((YAMLValue)element);
    }
    if (element instanceof YAMLDocument) {
      return new YamlEmptyObjectAdapter(element);
    }
    if (element instanceof LeafPsiElement leaf && leaf.getElementType() == YAMLTokenTypes.INDENT) {
      return YamlPropertyAdapter.createEmptyValueAdapter(element, true);
    }
    if (element instanceof LeafPsiElement) {
      JsonPropertyAdapter parentPropertyAdapter = getParentPropertyAdapter(element);
      return parentPropertyAdapter == null ? null : parentPropertyAdapter.getNameValueAdapter();
    }
    return null;
  }

  @Override
  public boolean allowsSingleQuotes() {
    return true;
  }

  @Override
  public boolean hasMissingCommaAfter(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public @Nullable JsonPropertyAdapter getParentPropertyAdapter(@NotNull PsiElement element) {
    YAMLMapping mapping = PsiTreeUtil.getParentOfType(element, YAMLMapping.class, true, YAMLKeyValue.class);
    if (mapping != null && (element instanceof YAMLScalar || element.getParent() instanceof YAMLScalar)) {
      // if we reach a mapping without reaching any key-value, this is a case like:
      // - foo: bar
      //   a
      // in such cases, we should create a property adapter for "a" for proper behavior of features
      return new YamlPropertyAdapter(element.getParent());
    }
    final YAMLKeyValue property = PsiTreeUtil.getParentOfType(element, YAMLKeyValue.class, false);
    if (property == null) return null;
    // it is a parent property only if its value or its key contains the current property,
    //  or it is the property itself (we perform a non-strict parent search)
    YAMLValue value = property.getValue();
    boolean isSelf = element == property;
    PsiElement key = property.getKey();
    boolean isKey = key != null && PsiTreeUtil.isAncestor(key, element, false);
    // don't jump up from sequences
    if (!isSelf && !isKey && value instanceof YAMLSequence && value != element) return null;
    boolean isValue = value != null && PsiTreeUtil.isAncestor(value, element, false);
    if (!isKey && !isValue && !isSelf) return null;
    return new YamlPropertyAdapter(property);
  }

  @Override
  public Set<String> getPropertyNamesOfParentObject(@NotNull PsiElement originalPosition, PsiElement computedPosition) {
    YAMLMapping object = PsiTreeUtil.getParentOfType(originalPosition, YAMLMapping.class, false);
    YAMLMapping otherObject = PsiTreeUtil.getParentOfType(computedPosition, YAMLMapping.class, false);
    // the original position can be either a sound element or a whitespace; whitespaces can belong to the parent
    if (object == null || otherObject != null
                          && PsiTreeUtil.isAncestor(CompletionUtil.getOriginalOrSelf(object),
                                                    CompletionUtil.getOriginalOrSelf(otherObject), true)) {
      object = otherObject;
    }
    if (object == null) return Collections.emptySet();
    return new YamlObjectAdapter(object).getPropertyList().stream().map(p -> p.getName()).collect(Collectors.toSet());
  }

  @Override
  public int indentOf(@NotNull PsiElement element) {
    return YAMLUtil.getIndentToThisElement(element);
  }

  @Override
  public int indentOf(@NotNull PsiFile file) {
    return CodeStyle.getSettings(file.getProject(), file.getVirtualFile()).getIndentOptionsByFile(file).INDENT_SIZE;
  }

  @Override
  public @Nullable JsonPointerPosition findPosition(@NotNull PsiElement element, boolean forceLastTransition) {
    JsonPointerPosition pos = new JsonPointerPosition();
    PsiElement current = element;
    while (!breakCondition(current)) {
      final PsiElement position = current;
      current = current.getParent();
      if (current instanceof YAMLSequence array) {
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
          pos.addPrecedingStep(idx);
        }
      }
      else if (current instanceof YAMLSequenceItem) {
        // do nothing - handled by the upper condition
      }
      else if (current instanceof YAMLKeyValue) {
        final String propertyName = StringUtil.notNullize(((YAMLKeyValue)current).getName());
        current = current.getParent();
        if (!(current instanceof YAMLMapping)) return null;//incorrect syntax?
        pos.addPrecedingStep(propertyName);
      }
      else if (current instanceof YAMLMapping && position instanceof YAMLKeyValue) {
        // if either value or not first in the chain - needed for completion variant
        final String propertyName = StringUtil.notNullize(((YAMLKeyValue)position).getName());
        if (propertyName.contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)) continue;
        if (position != element || forceLastTransition) {
          pos.addPrecedingStep(propertyName);
        }
      }
      else if (breakCondition(current)) {
        break;
      }
      else {
        if (current instanceof YAMLMapping) {
          List<YAMLPsiElement> elements = ((YAMLMapping)current).getYAMLElements();
          if (elements.isEmpty()) return null;
          if (position instanceof YAMLPsiElement && elements.contains(position)) {
            continue;
          }
        }
        return null;//something went wrong
      }
    }
    return pos;
  }

  private static boolean breakCondition(PsiElement current) {
    return current instanceof PsiFile || current instanceof YAMLDocument ||
           current instanceof YAMLBlockMappingImpl && current.getParent() instanceof YAMLDocument;
  }

  @Override
  public boolean requiresValueQuotes() {
    return false;
  }

  @Override
  public String getDefaultObjectValue() {
    return "";
  }

  @Override
  public String getDefaultArrayValue() {
    return "- ";
  }

  @Override
  public boolean hasWhitespaceDelimitedCodeBlocks() {
    return true;
  }

  @Override
  public String getNodeTextForValidation(PsiElement element) {
    String text = element.getText();
    if (!StringUtil.startsWith(text, "!!") && !StringUtil.startsWithChar(text, '&')) return text;
    // remove tags
    int spaceIndex = text.indexOf(' ');
    return spaceIndex > 0 ? text.substring(spaceIndex + 1) : text;
  }

  @Override
  public TextRange adjustErrorHighlightingRange(@NotNull PsiElement element) {
    YAMLAnchor[] anchors = PsiTreeUtil.getChildrenOfType(element, YAMLAnchor.class);
    if (anchors != null && anchors.length > 0) {
      YAMLAnchor lastAnchor = anchors[anchors.length - 1];
      PsiElement next = PsiTreeUtil.skipWhitespacesAndCommentsForward(lastAnchor);
      return next == null ? element.getTextRange() : next.getTextRange();
    }
    PsiElement parent = element.getParent();
    if (parent instanceof YAMLDocument) {
      PsiElement firstYamlChild = element.getFirstChild();
      while (firstYamlChild != null && !(firstYamlChild instanceof YAMLPsiElement)) {
        firstYamlChild = firstYamlChild.getNextSibling();
      }
      return firstYamlChild == null ? element.getTextRange() : firstYamlChild.getTextRange();
    }
    return element.getTextRange();
  }

  @Override
  public JsonLikeSyntaxAdapter getSyntaxAdapter(Project project) {
    return YamlJsonLikeSyntaxAdapter.INSTANCE;
  }

  @Override
  public PsiElement getParentContainer(PsiElement element) {
    return PsiTreeUtil.getParentOfType(PsiTreeUtil.getParentOfType(element, YAMLKeyValue.class),
                                       YAMLMapping.class, YAMLSequence.class);
  }

  @Override
  public @NotNull Collection<PsiElement> getRoots(@NotNull PsiFile file) {
    if (!(file instanceof YAMLFile)) return ContainerUtil.emptyList();
    Collection<PsiElement> roots = new HashSet<>();
    for (YAMLDocument document : ((YAMLFile)file).getDocuments()) {
      YAMLValue topLevelValue = document.getTopLevelValue();
      roots.add(topLevelValue == null ? document : topLevelValue);
    }
    return roots;
  }

  @Override
  @Deprecated(forRemoval = true)
  public boolean requiresReformatAfterArrayInsertion() {
    return false;
  }

  @Override
  public @Nullable PsiElement getPropertyNameElement(PsiElement property) {
    return property instanceof YAMLKeyValue ? ((YAMLKeyValue)property).getKey() : null;
  }

  private static class YamlJsonLikeSyntaxAdapter implements JsonLikeSyntaxAdapter {
    private static final YamlJsonLikeSyntaxAdapter INSTANCE = new YamlJsonLikeSyntaxAdapter();
    @Override
    public @NotNull PsiElement adjustValue(@NotNull PsiElement value) {
      if (!(value instanceof YAMLValue)) {
        YAMLKeyValue keyValue = findPrecedingKeyValueWithNoValue(value);
        if (keyValue == null) {
          keyValue = ObjectUtils.tryCast(value.getParent(), YAMLKeyValue.class);
        }
        if (keyValue != null) {
          YAMLValue adjustedValue = keyValue.getValue();
          if (adjustedValue != null) return adjustedValue;
          YAMLElementGenerator generator = YAMLElementGenerator.getInstance(keyValue.getProject());
          YAMLValue newValue = Objects.requireNonNull(generator.createYamlKeyValue("a", "\"\"").getValue());
          return keyValue.add(newValue);
        }
        return value;
      }
      YAMLAnchor[] anchors = PsiTreeUtil.getChildrenOfType(value, YAMLAnchor.class);
      if (anchors == null || anchors.length == 0) return value;
      PsiElement next = PsiTreeUtil.skipWhitespacesAndCommentsForward(anchors[anchors.length - 1]);
      return next == null ? value : next;
    }

    private static YAMLKeyValue findPrecedingKeyValueWithNoValue(PsiElement element) {
      PsiElement prev = PsiUtilCore.getElementType(element) == YAMLTokenTypes.INDENT ? element.getPrevSibling() : element;
      prev = PsiTreeUtil.skipWhitespacesAndCommentsBackward(prev);
      if (prev instanceof YAMLKeyValue keyValue && (keyValue).getValue() == null) {
        return keyValue;
      }
      return null;
    }

    @Override
    public @NotNull PsiElement createProperty(@NotNull String name, @NotNull String value, @NotNull Project project) {
      YAMLElementGenerator generator = YAMLElementGenerator.getInstance(project);
      return generator.createYamlKeyValue(name, StringUtil.unquoteString(value));
    }

    @Override
    public @NotNull PsiElement createEmptyArray(@NotNull Project project, boolean preferInline) {
      YAMLElementGenerator generator = YAMLElementGenerator.getInstance(project);
      return preferInline ? generator.createEmptyArray() : generator.createEmptySequence();
    }

    @Nullable
    private static PsiElement skipWsBackward(@Nullable PsiElement item) {
      while (item instanceof PsiWhiteSpace || item instanceof PsiComment) {
        item = PsiTreeUtil.prevLeaf(item);
      }
      return item;
    }

    @Nullable
    private static PsiElement skipWsForward(@Nullable PsiElement item) {
      while (item instanceof PsiWhiteSpace || item instanceof PsiComment) {
        item = PsiTreeUtil.nextLeaf(item);
      }
      return item;
    }

    @Override
    public void removeArrayItem(@NotNull PsiElement item) {
      PsiElement parent = item instanceof YAMLSequenceItem ? item : item.getParent();
      if (parent instanceof YAMLSequenceItem) {
        PsiElement grandParent = parent.getParent();
        PsiElement prev = skipWsBackward(PsiTreeUtil.prevLeaf(parent));
        PsiElement next = skipWsForward(PsiTreeUtil.nextLeaf(parent));
        parent.delete();
        if (grandParent instanceof YAMLArrayImpl && prev instanceof LeafPsiElement && ((LeafPsiElement)prev).getElementType() == YAMLTokenTypes.COMMA) {
          prev.delete();
        }
        else if (grandParent instanceof YAMLArrayImpl && next instanceof LeafPsiElement && ((LeafPsiElement)next).getElementType() == YAMLTokenTypes.COMMA) {
          next.delete();
        }
        if (!(grandParent instanceof YAMLArrayImpl) && prev instanceof LeafPsiElement && ((LeafPsiElement)prev).getElementType() == YAMLTokenTypes.EOL) {
          prev.delete();
        }
        else if (!(grandParent instanceof YAMLArrayImpl) && next instanceof LeafPsiElement && ((LeafPsiElement)next).getElementType() == YAMLTokenTypes.EOL) {
          next.delete();
        }
      }
      else {
        throw new IllegalArgumentException("Cannot remove item from a non-sequence element");
      }
    }

    @Override
    public @NotNull PsiElement addArrayItem(@NotNull PsiElement array, @NotNull String itemValue) {
      if (array instanceof YAMLArrayImpl) {
        return addInlineArrayItem((YAMLSequence)array, itemValue);
      }
      else if (array instanceof YAMLSequence) {
        return addSequenceItem((YAMLSequence)array, itemValue);
      }
      else {
        throw new IllegalArgumentException("Cannot add item to a non-sequence element");
      }
    }

    private static PsiElement addInlineArrayItem(@NotNull YAMLSequence array, @NotNull String itemValue) {
      YAMLElementGenerator generator = YAMLElementGenerator.getInstance(array.getProject());
      YAMLSequenceItem sequenceItem = generator.createArrayItem(itemValue);

      PsiElement addedItem = array.addBefore(sequenceItem, array.getLastChild()); // we insert before closing bracket ']'
      if (array.getItems().size() > 1) {
        array.addAfter(generator.createComma(), PsiTreeUtil.skipWhitespacesAndCommentsBackward(addedItem));
      }
      return addedItem;
    }

    private static PsiElement addSequenceItem(@NotNull YAMLSequence sequence, @NotNull String itemValue) {
      YAMLElementGenerator generator = YAMLElementGenerator.getInstance(sequence.getProject());
      YAMLSequenceItem sequenceItem = generator.createSequenceItem(itemValue);

      PsiElement lastChild = sequence.getLastChild();
      if (lastChild != null && lastChild.getNode().getElementType() != YAMLTokenTypes.EOL) {
        sequence.add(generator.createEol());
      }
      List<YAMLSequenceItem> items = sequence.getItems();
      YAMLSequenceItem lastItem = items.get(items.size() - 1);

      int indent = lastChild != null ? YAMLUtil.getIndentToThisElement(lastItem) : YAMLUtil.getIndentToThisElement(sequence) + 2;
      sequence.add(generator.createIndent(indent));
      return sequence.add(sequenceItem);
    }

    @Override
    public void ensureComma(PsiElement self, PsiElement newElement) {
      if (newElement instanceof YAMLKeyValue && self instanceof YAMLKeyValue) {
        PsiElement sibling = skipSiblingsForward(self);
        if (sibling != null && sibling.getText().equals("\n")) return;
        PsiElement parent = self.getParent();
        parent.addAfter(generateSeparator(parent), self);
        // two EOLs at the top level after a compound value
        if (parent.getParent() instanceof YAMLDocument && isCompoundValue(((YAMLKeyValue)self).getValue())) {
          parent.addAfter(generateSeparator(parent), self);
        }
      }
    }

    private static boolean isCompoundValue(@Nullable YAMLValue value) {
      return value instanceof YAMLMapping || value instanceof YAMLSequence;
    }

    private static @Nullable PsiElement skipSiblingsForward(@Nullable PsiElement element, @NotNull Class<? extends PsiElement> @NotNull ... elementClasses) {
      if (element != null) {
        for (PsiElement e = element.getNextSibling(); e != null; e = e.getNextSibling()) {
          if (!(e instanceof PsiComment) && (!(e instanceof PsiWhiteSpace) || e.textContains('\n'))) {
            return e;
          }
        }
      }
      return null;
    }

    private static @NotNull PsiElement generateSeparator(PsiElement parent) {
      YAMLElementGenerator generator = YAMLElementGenerator.getInstance(parent.getProject());
      if (parent instanceof YAMLHashImpl) {
        return generator.createComma();
      } else {
        return generator.createEol();
      }
    }

    @Override
    public void removeIfComma(PsiElement forward) {
      if (forward instanceof LeafPsiElement && ((LeafPsiElement)forward).getElementType() == YAMLTokenTypes.EOL) {
        PsiElement nextSibling;
        while ((nextSibling = forward.getNextSibling()) instanceof LeafPsiElement
               && ((LeafPsiElement)nextSibling).getElementType() == YAMLTokenTypes.INDENT) {
          nextSibling.delete();
        }
        forward.delete();
      }
    }

    @Override
    public boolean fixWhitespaceBefore(PsiElement initialElement, PsiElement element) {
      return initialElement instanceof YAMLValue && initialElement != element;
    }

    @Override
    public @NotNull String getDefaultValueFromType(@Nullable JsonSchemaType type) {
      if (type == null) return "";
      if (type == JsonSchemaType._object) return " ";
      if (type == JsonSchemaType._array) return " - ";
      return type.getDefaultValue();
    }

    @Override
    public @NotNull PsiElement adjustNewProperty(@NotNull PsiElement element) {
      if (element instanceof YAMLMapping) return element.getFirstChild();
      return element;
    }

    @Override
    public @NotNull PsiElement adjustPropertyAnchor(@NotNull LeafPsiElement element) {
      YAMLElementGenerator generator = YAMLElementGenerator.getInstance(element.getProject());
      YAMLKeyValue keyValue = findPrecedingKeyValueWithNoValue(element);
      assert keyValue != null : "Should come here only for YAMLKeyValue with no value and a following indent";
      PsiComment nextComment = ObjectUtils.tryCast(skipNonNewlineSpaces(keyValue), PsiComment.class);
      if (nextComment != null) {
        keyValue.addBefore(generator.createSpace(), null);
        keyValue.addBefore(nextComment.copy(), null);
      }
      keyValue.addBefore(generator.createEol(), null);
      keyValue.addBefore(generator.createIndent(element.getTextLength()), null);
      PsiElement prev = element.getPrevSibling();
      if (prev != null) prev.delete();
      element.delete();
      if (nextComment != null) nextComment.delete();
      return keyValue;
    }

    private static @Nullable PsiElement skipNonNewlineSpaces(YAMLKeyValue keyValue) {
      PsiElement sibling = keyValue.getNextSibling();
      while (sibling instanceof PsiWhiteSpace && !sibling.getText().contains("\n")) {
        sibling = sibling.getNextSibling();
      }
      return sibling;
    }

    @NotNull
    @Override
    public PsiElement addProperty(@NotNull PsiElement contextForInsertion, @NotNull PsiElement newProperty) {
      // Sometimes, post-write-action formatting can break the YAML structure if the area was not indented properly initially.
      // This is why we pre-format it to avoid problems.
      preFormatAround(contextForInsertion);

      return JsonLikeSyntaxAdapter.super.addProperty(contextForInsertion, newProperty);
    }

    private static void preFormatAround(PsiElement element) {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(element.getProject());
      Document document = documentManager.getDocument(element.getContainingFile());
      if (document == null) {
        return; // nothing to format if there is no document anyway
      }
      // We need to commit the pending PSI changes before triggering the formatting, otherwise it will fail.
      // This typically happens if we have several calls to addProperty in a row, but could be with any previous PSI change too.
      documentManager.doPostponedOperationsAndUnblockDocument(document);

      // If we try to format an element that is itself indented, the formatter will not take this base indent into account.
      // This is why we need to go up the tree to find the top-level Key-Value that contains our element.
      PsiElement elementToFormat = YamlPsiUtilKt.findClosestAncestorWithoutIndent(document, element);

      // The formatter doesn't support formatting YAMLDocument or YAMLMapping elements, but if we reach one of those, they represent the
      // whole file anyway (because they must have an indent of 0), so we can trigger the formatting on the containing file.
      if (elementToFormat instanceof YAMLDocument || elementToFormat instanceof YAMLMapping) {
        elementToFormat = elementToFormat.getContainingFile();
      }
      CodeStyleManager.getInstance(element.getProject()).reformat(elementToFormat, true);
    }
  }
}
