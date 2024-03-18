package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementGenerator;
import org.jetbrains.yaml.YAMLElementTypes;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.Collection;
import java.util.List;

public class YAMLBlockMappingImpl extends YAMLMappingImpl {
  public static final String EMPTY_MAP_MESSAGE = "YAML map without any key-value";

  public YAMLBlockMappingImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  public YAMLKeyValue getFirstKeyValue() {
    YAMLKeyValue firstKeyValue = findChildByType(YAMLElementTypes.KEY_VALUE_PAIR);
    if (firstKeyValue == null) {
      throw new IllegalStateException(EMPTY_MAP_MESSAGE);
    }
    return firstKeyValue;
  }

  private void addNewKeyToTheEnd(@NotNull YAMLKeyValue key) {
    final int indent = YAMLUtil.getIndentToThisElement(this);

    final YAMLElementGenerator generator = YAMLElementGenerator.getInstance(getProject());
    IElementType lastChildType = PsiUtilCore.getElementType(getLastChild());
    if (indent == 0) {
      if (lastChildType != YAMLTokenTypes.EOL) {
        add(generator.createEol());
      }
    }
    else if (!(lastChildType == YAMLTokenTypes.INDENT && getLastChild().getTextLength() == indent)) {
      add(generator.createEol());
      add(generator.createIndent(indent));
    }
    add(key);
  }

  @Override
  protected void addNewKey(@NotNull YAMLKeyValue key) {
    final int indent = YAMLUtil.getIndentToThisElement(this);
    ASTNode node = getNode();
    ASTNode place = node.getLastChildNode();
    ASTNode whereInsert = null;
    while(place != null) {
      if(place.getElementType() == YAMLTokenTypes.INDENT && place.getTextLength() == indent) {
        whereInsert = place;
      }
      else if (place.getElementType() == YAMLTokenTypes.EOL) {
        ASTNode next = place.getTreeNext();
        if (next == null || next.getElementType() == YAMLTokenTypes.EOL) {
          whereInsert = place;
        }
      }
      else {
        break;
      }
      place = place.getTreePrev();
    }

    final YAMLElementGenerator generator = YAMLElementGenerator.getInstance(getProject());
    if (whereInsert == null) {
      add(generator.createEol());
      if (indent != 0) {
        add(generator.createIndent(indent));
      }
      add(key);
      return;
    }

    PsiElement anchor = whereInsert.getPsi();
    if (indent == 0 || whereInsert.getElementType() == YAMLTokenTypes.INDENT && getLastChild().getTextLength() == indent) {
      addAfter(key, anchor);
      return;
    }
    if (whereInsert.getElementType() != YAMLTokenTypes.EOL) {
      anchor = addAfter(generator.createEol(), anchor);
    }
    addAfter(generator.createIndent(indent), anchor);
    addAfter(key, anchor);
  }

  /**
   * This method inserts key-value pair somewhere near specified absolute offset.
   * The offset could be beyond borders of this mapping.
   */
  public void insertKeyValueAtOffset(@NotNull YAMLKeyValue keyValue, int offset) {
    int indent = YAMLUtil.getIndentToThisElement(this);

    if (offset < getTextRange().getStartOffset()) {
      offset = getTextRange().getStartOffset();
    }

    YAMLElementGenerator generator = YAMLElementGenerator.getInstance(getProject());
    if (offset == getTextRange().getStartOffset()) {
      boolean pasteAtEmptyFirstMappingLine = PsiUtilCore.getElementType(getPrevSibling()) == YAMLTokenTypes.INDENT &&
                                             PsiUtilCore.getElementType(getFirstChild()) == YAMLTokenTypes.EOL;
      PsiElement newElement = addBefore(keyValue, getFirstChild());
      if (!pasteAtEmptyFirstMappingLine) {
        newElement = addAfter(generator.createEol(), newElement);
        addAfter(generator.createIndent(indent), newElement);
      }
      return;
    }

    if (offset == getTextRange().getEndOffset()) {
      addNewKeyToTheEnd(keyValue);
      return;
    }

    if (offset > getTextRange().getEndOffset()) {
      PsiElement nextLeaf = PsiTreeUtil.nextLeaf(this);
      List<PsiElement> toBeRemoved = new SmartList<>();
      while (YAMLElementTypes.SPACE_ELEMENTS.contains(PsiUtilCore.getElementType(nextLeaf))) {
        if (offset >= nextLeaf.getTextRange().getStartOffset()) {
          toBeRemoved.add(nextLeaf);
        }
        nextLeaf = PsiTreeUtil.nextLeaf(nextLeaf);
      }
      for (PsiElement leaf : toBeRemoved) {
        add(leaf);
      }
      for (PsiElement leaf : toBeRemoved) {
        leaf.delete();
      }

      addNewKeyToTheEnd(keyValue);
      return;
    }

    PsiElement child = getFirstChild();
    for (; child != null; child = child.getNextSibling()) {
      if (PsiUtilCore.getElementType(child) == YAMLTokenTypes.INDENT && offset <= child.getTextRange().getEndOffset()) {
        if (PsiUtilCore.getElementType(child.getNextSibling()) == YAMLTokenTypes.EOL) {
          addAfter(keyValue, child);
          return;
        }
        PsiElement newElement = addBefore(generator.createIndent(indent), child);
        newElement = addAfter(keyValue, newElement);
        addAfter(generator.createEol(), newElement);
        return;
      }
      if (offset <= child.getTextRange().getEndOffset()) {
        break;
      }
    }
    for (; child != null; child = child.getNextSibling()) {
      if (PsiUtilCore.getElementType(child) == YAMLTokenTypes.EOL) {
        PsiElement element = child;
        if (indent != 0) {
          element = addAfter(generator.createIndent(indent), element);
        }
        element = addAfter(keyValue, element);
        if (PsiUtilCore.getElementType(child) != YAMLTokenTypes.EOL) {
          addAfter(generator.createEol(), element);
        }
        return;
      }
    }
    addNewKeyToTheEnd(keyValue);
  }

  /** @return deepest created or found key or null if nothing could be created */
  @Nullable
  public YAMLKeyValue getOrCreateKeySequence(@NotNull List<String> keyComponents, int preferableOffset) {
    if (keyComponents.isEmpty()) {
      return null;
    }
    String head = keyComponents.iterator().next();
    List<String> tail = ContainerUtil.subList(keyComponents, 1);

    YAMLKeyValue keyValue = getKeyValueByKey(head);
    if (keyValue == null) {
      int indent = YAMLUtil.getIndentToThisElement(this);
      String text = YAMLElementGenerator.createChainedKey(keyComponents, indent);
      final YAMLElementGenerator generator = YAMLElementGenerator.getInstance(getProject());
      Collection<YAMLKeyValue> values = PsiTreeUtil.collectElementsOfType(generator.createDummyYamlWithText(text), YAMLKeyValue.class);
      if (values.isEmpty()) {
        Logger.getInstance(YAMLBlockMappingImpl.class).error(
          "No one key-value created: input sequence = " + keyComponents + " generated text = '" + text + "'"
        );
        return null;
      }
      YAMLKeyValue newKeyValue = values.iterator().next();
      insertKeyValueAtOffset(newKeyValue, preferableOffset);
      keyValue = getKeyValueByKey(head);
      assert keyValue != null;
    }

    if (keyComponents.size() == 1) {
      return keyValue;
    }
    else if (keyValue.getValue() instanceof YAMLBlockMappingImpl) { // TODO: support JSON-like mappings (create minor issue)
      return ((YAMLBlockMappingImpl)keyValue.getValue()).getOrCreateKeySequence(tail, preferableOffset);
    }
    else {
      return null;
    }
  }
}
