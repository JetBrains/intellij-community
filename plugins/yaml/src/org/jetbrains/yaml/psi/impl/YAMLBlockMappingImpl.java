package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementGenerator;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.List;

public class YAMLBlockMappingImpl extends YAMLMappingImpl {
  public YAMLBlockMappingImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  protected void addNewKey(@NotNull YAMLKeyValue key) {
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

    if (offset >= getTextRange().getEndOffset()) {
      addNewKey(keyValue);
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
        PsiElement newElement = addAfter(generator.createIndent(indent), child);
        newElement = addAfter(keyValue, newElement);
        addAfter(generator.createEol(), newElement);
        return;
      }
    }
    addNewKey(keyValue);
  }

  /** @return deepest created or found key or null if nothing could be created */
  @Nullable
  public YAMLKeyValue getOrCreateKeySequence(@NotNull List<String> keyComponents, int preferableOffset) {
    if (keyComponents.isEmpty()) {
      return null;
    }
    String head = keyComponents.iterator().next();
    List<String> tail = keyComponents.subList(1, keyComponents.size());

    YAMLKeyValue keyValue = getKeyValueByKey(head);
    if (keyValue == null) {
      int indent = YAMLUtil.getIndentToThisElement(this);
      String text = YAMLElementGenerator.createChainedKey(keyComponents, indent);
      final YAMLElementGenerator generator = YAMLElementGenerator.getInstance(getProject());
      YAMLKeyValue newKeyValue =
        PsiTreeUtil.collectElementsOfType(generator.createDummyYamlWithText(text), YAMLKeyValue.class).iterator().next();
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
