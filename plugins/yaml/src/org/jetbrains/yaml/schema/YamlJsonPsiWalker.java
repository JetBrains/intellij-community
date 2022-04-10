// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
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
import org.jetbrains.yaml.psi.*;
import org.jetbrains.yaml.psi.impl.YAMLBlockMappingImpl;

import java.util.*;
import java.util.stream.Collectors;

public final class YamlJsonPsiWalker implements JsonLikePsiWalker {
  public static final YamlJsonPsiWalker INSTANCE = new YamlJsonPsiWalker();

  private YamlJsonPsiWalker() {
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

  @Nullable
  @Override
  public JsonValueAdapter createValueAdapter(@NotNull PsiElement element) {
    return element instanceof YAMLValue ? YamlPropertyAdapter.createValueAdapterByType((YAMLValue)element)
                                        : (element instanceof YAMLDocument ? new YamlEmptyObjectAdapter(element) : null);
  }

  @Override
  public boolean allowsSingleQuotes() {
    return true;
  }

  @Override
  public boolean hasMissingCommaAfter(@NotNull PsiElement element) {
    return false;
  }

  @Nullable
  @Override
  public JsonPropertyAdapter getParentPropertyAdapter(@NotNull PsiElement element) {
    YAMLMapping mapping = PsiTreeUtil.getParentOfType(element, YAMLMapping.class, true, YAMLKeyValue.class);
    if (mapping != null) {
      // if we reach a mapping without reaching any key-value, this is a case like:
      // - foo: bar
      //   a
      // and we should create a property adapter for "a" for proper behavior of features
      return new YamlPropertyAdapter(element.getParent());
    }
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
    YAMLMapping otherObject = PsiTreeUtil.getParentOfType(computedPosition, YAMLMapping.class);
    // the original position can be either a sound element or a whitespace; whitespaces can belong to the parent
    if (object == null || otherObject != null
                          && PsiTreeUtil.isAncestor(CompletionUtil.getOriginalOrSelf(object),
                                                    CompletionUtil.getOriginalOrSelf(otherObject), true)) {
      object = otherObject;
    }
    if (object == null) return Collections.emptySet();
    return new YamlObjectAdapter(object).getPropertyList().stream().map(p -> p.getName()).collect(Collectors.toSet());
  }

  @Nullable
  @Override
  public JsonPointerPosition findPosition(@NotNull PsiElement element, boolean forceLastTransition) {
    JsonPointerPosition pos = new JsonPointerPosition();
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
          pos.addPrecedingStep(idx);
        }
      } else if (current instanceof YAMLSequenceItem) {
        // do nothing - handled by the upper condition
      } else if (current instanceof YAMLKeyValue) {
        final String propertyName = StringUtil.notNullize(((YAMLKeyValue)current).getName());
        current = current.getParent();
        if (!(current instanceof YAMLMapping)) return null;//incorrect syntax?
        pos.addPrecedingStep(propertyName);
      } else if (current instanceof YAMLMapping && position instanceof YAMLKeyValue) {
        // if either value or not first in the chain - needed for completion variant
        final String propertyName = StringUtil.notNullize(((YAMLKeyValue)position).getName());
        if (propertyName.contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)) continue;
        if (position != element || forceLastTransition) {
          pos.addPrecedingStep(propertyName);
        }
      } else if (breakCondition(current)) {
        break;
      } else {
        if (current instanceof YAMLMapping) {
          List<YAMLPsiElement> elements = ((YAMLMapping)current).getYAMLElements();
          if (elements.size() == 0) return null;
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
    if (anchors == null || anchors.length == 0) return element.getTextRange();
    YAMLAnchor lastAnchor = anchors[anchors.length - 1];
    PsiElement next = PsiTreeUtil.skipWhitespacesAndCommentsForward(lastAnchor);
    return next == null ? element.getTextRange() : next.getTextRange();
  }

  @Override
  public JsonLikeSyntaxAdapter getSyntaxAdapter(Project project) {
    return new JsonLikeSyntaxAdapter() {
      private final YAMLElementGenerator myGenerator = YAMLElementGenerator.getInstance(project);

      @Nullable
      @Override
      public PsiElement getPropertyValue(PsiElement property) {
        assert property instanceof YAMLKeyValue;
        YAMLValue value = ((YAMLKeyValue)property).getValue();
        if (value == null) return null;
        return adjustValue(value);
      }

      @NotNull
      @Override
      public PsiElement adjustValue(@NotNull PsiElement value) {
        if (!(value instanceof YAMLValue)) return value;
        YAMLAnchor[] anchors = PsiTreeUtil.getChildrenOfType(value, YAMLAnchor.class);
        if (anchors == null || anchors.length == 0) return value;
        PsiElement next = PsiTreeUtil.skipWhitespacesAndCommentsForward(anchors[anchors.length - 1]);
        return next == null ? value : next;
      }

      @Nullable
      @Override
      public String getPropertyName(PsiElement property) {
        assert property instanceof YAMLKeyValue;
        return ((YAMLKeyValue)property).getName();
      }

      private YAMLKeyValue findPrecedingKeyValueWithNoValue(PsiElement element) {
        if (PsiUtilCore.getElementType(element) == YAMLTokenTypes.INDENT) {
          PsiElement prev = element.getPrevSibling();
          prev = prev == null ? null : PsiTreeUtil.skipWhitespacesAndCommentsBackward(prev);
          if (prev instanceof YAMLKeyValue && ((YAMLKeyValue)prev).getValue() == null) {
            return (YAMLKeyValue)prev;
          }
        }
        return null;
      }

      @NotNull
      @Override
      public PsiElement createProperty(@NotNull String name, @NotNull String value, PsiElement element) {
        YAMLKeyValue keyValue = myGenerator.createYamlKeyValue(name, StringUtil.unquoteString(value));
        return element instanceof YAMLDocument || findPrecedingKeyValueWithNoValue(element) != null
               ? myGenerator.createDummyYamlWithText(keyValue.getText()).getDocuments().get(0).getFirstChild()
               : keyValue;
      }

      @Override
      public boolean ensureComma(PsiElement self, PsiElement newElement) {
        if (newElement instanceof YAMLKeyValue && self instanceof YAMLKeyValue) {
          self.getParent().addAfter(myGenerator.createEol(), self);
        }
        return false;
      }

      @Override
      public void removeIfComma(PsiElement forward) {
        if (forward instanceof LeafPsiElement && ((LeafPsiElement)forward).getElementType() == YAMLTokenTypes.EOL) {
          PsiElement nextSibling;
          while ((nextSibling = forward.getNextSibling()) instanceof LeafPsiElement
                 && ((LeafPsiElement)nextSibling).getElementType() == YAMLTokenTypes.INDENT){
            nextSibling.delete();
          }
          forward.delete();
        }
      }

      @Override
      public boolean fixWhitespaceBefore(PsiElement initialElement, PsiElement element) {
        return initialElement instanceof YAMLValue && initialElement != element;
      }

      @NotNull
      @Override
      public String getDefaultValueFromType(@Nullable JsonSchemaType type) {
        if (type == null) return "";
        if (type == JsonSchemaType._object) return " ";
        if (type == JsonSchemaType._array) return " - ";
        return type.getDefaultValue();
      }

      @Override
      public PsiElement adjustNewProperty(PsiElement element) {
        if (element instanceof YAMLMapping) return element.getFirstChild();
        return element;
      }

      @Override
      public PsiElement adjustPropertyAnchor(LeafPsiElement element) {
        YAMLKeyValue keyValue = findPrecedingKeyValueWithNoValue(element);
        assert keyValue != null: "Should come here only for YAMLKeyValue with no value and a following indent";
        PsiComment nextComment = ObjectUtils.tryCast(skipNonNewlineSpaces(keyValue), PsiComment.class);
        if (nextComment != null) {
          keyValue.addBefore(myGenerator.createSpace(), null);
          keyValue.addBefore(nextComment.copy(), null);
        }
        keyValue.addBefore(myGenerator.createEol(), null);
        keyValue.addBefore(myGenerator.createIndent(element.getTextLength()), null);
        PsiElement prev = element.getPrevSibling();
        if (prev != null) prev.delete();
        element.delete();
        if (nextComment != null) nextComment.delete();
        return keyValue;
      }

      @Nullable
      private PsiElement skipNonNewlineSpaces(YAMLKeyValue keyValue) {
        PsiElement sibling = keyValue.getNextSibling();
        while (sibling instanceof PsiWhiteSpace && !sibling.getText().contains("\n")) {
          sibling = sibling.getNextSibling();
        }
        return sibling;
      }
    };
  }

  @Override
  public PsiElement getParentContainer(PsiElement element) {
    return PsiTreeUtil.getParentOfType(PsiTreeUtil.getParentOfType(element, YAMLKeyValue.class),
                                       YAMLMapping.class, YAMLSequence.class);
  }

  @NotNull
  @Override
  public Collection<PsiElement> getRoots(@NotNull PsiFile file) {
    if (!(file instanceof YAMLFile)) return ContainerUtil.emptyList();
    Collection<PsiElement> roots = new HashSet<>();
    for (YAMLDocument document : ((YAMLFile)file).getDocuments()) {
      YAMLValue topLevelValue = document.getTopLevelValue();
      roots.add(topLevelValue == null ? document : topLevelValue);
    }
    return roots;
  }

  @Nullable
  @Override
  public PsiElement getPropertyNameElement(PsiElement property) {
    return property instanceof YAMLKeyValue ? ((YAMLKeyValue)property).getKey() : null;
  }
}
