package com.intellij.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CharTable;

/**
 *
 */
public class DebugUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.DebugUtil");

  public static /*final*/ boolean CHECK = false;
  public static final boolean CHECK_INSIDE_ATOMIC_ACTION_ENABLED = true;

  public static String psiTreeToString(PsiElement element, boolean skipWhitespaces) {
    return treeToString(SourceTreeToPsiMap.psiElementToTree(element), skipWhitespaces);
  }

  public static String treeToString(TreeElement root, boolean skipWhitespaces) {
    StringBuffer buffer = new StringBuffer();
    treeToBuffer(buffer, root, 0, skipWhitespaces);
    return buffer.toString();
  }

  public static String treeToStringWithUserData(TreeElement root, boolean skipWhitespaces) {
    StringBuffer buffer = new StringBuffer();
    treeToBufferWithUserData(buffer, root, 0, skipWhitespaces);
    return buffer.toString();
  }

  public static String treeToStringWithUserData(PsiElement root, boolean skipWhitespaces) {
    StringBuffer buffer = new StringBuffer();
    treeToBufferWithUserData(buffer, root, 0, skipWhitespaces);
    return buffer.toString();
  }

  private static void treeToBuffer(StringBuffer buffer, TreeElement root, int indent, boolean skipWhiteSpaces) {
    if (skipWhiteSpaces && root.getElementType() == ElementType.WHITE_SPACE) return;

    for(int i = 0; i < indent; i++){
      buffer.append(' ');
    }
    if (root instanceof CompositeElement){
      buffer.append(SourceTreeToPsiMap.treeElementToPsi(root).toString());
    }
    else{
      String text = root.getText();
      text = StringUtil.replace(text, "\n", "\\n");
      text = StringUtil.replace(text, "\r", "\\r");
      text = StringUtil.replace(text, "\t", "\\t");
      buffer.append(root.toString() + "('" + text + "')");
    }
    buffer.append("\n");
    if (root instanceof CompositeElement){
      ChameleonTransforming.transformChildren((CompositeElement)root);
      TreeElement child = ((CompositeElement)root).firstChild;

      if (child == null){
        for(int i = 0; i < indent + 2; i++){
          buffer.append(' ');
        }
        buffer.append("<empty list>\n");
      }
      else while(child != null){
        treeToBuffer(buffer, child, indent + 2, skipWhiteSpaces);
        child = child.getTreeNext();
      }
    }
  }                                              

  private static void treeToBufferWithUserData(StringBuffer buffer, TreeElement root, int indent, boolean skipWhiteSpaces) {
    if (skipWhiteSpaces && root.getElementType() == ElementType.WHITE_SPACE) return;

    for(int i = 0; i < indent; i++){
      buffer.append(' ');
    }
    if (root instanceof CompositeElement){
      buffer.append(SourceTreeToPsiMap.treeElementToPsi(root).toString());
    }
    else{
      String text = root.getText();
      text = StringUtil.replace(text, "\n", "\\n");
      text = StringUtil.replace(text, "\r", "\\r");
      text = StringUtil.replace(text, "\t", "\\t");
      buffer.append(root.toString() + "('" + text + "')");
    }
    buffer.append(root.getUserDataString());
    buffer.append("\n");
    if (root instanceof CompositeElement || root instanceof ChameleonElement){
      PsiElement[] children = SourceTreeToPsiMap.treeElementToPsi(root).getChildren();

      for(int i = 0; i < children.length; i++){
        PsiElement child = children[i];
        treeToBufferWithUserData(buffer, SourceTreeToPsiMap.psiElementToTree(child), indent + 2, skipWhiteSpaces);
      }

      if (children.length == 0){
        for(int i = 0; i < indent + 2; i++){
          buffer.append(' ');
        }
        buffer.append("<empty list>\n");
      }
    }
  }

  private static void treeToBufferWithUserData(StringBuffer buffer, PsiElement root, int indent, boolean skipWhiteSpaces) {
    if (skipWhiteSpaces && root instanceof PsiWhiteSpace) return;

    for(int i = 0; i < indent; i++){
      buffer.append(' ');
    }
    if (root instanceof CompositeElement){
      buffer.append(root);
    }
    else{
      String text = root.getText();
      text = StringUtil.replace(text, "\n", "\\n");
      text = StringUtil.replace(text, "\r", "\\r");
      text = StringUtil.replace(text, "\t", "\\t");
      buffer.append(root.toString() + "('" + text + "')");
    }
    buffer.append(((UserDataHolderBase)root).getUserDataString());
    buffer.append("\n");

    PsiElement[] children = root.getChildren();

    for(int i = 0; i < children.length; i++){
      PsiElement child = children[i];
      treeToBufferWithUserData(buffer, child, indent + 2, skipWhiteSpaces);
    }

    if (children.length == 0){
      for(int i = 0; i < indent + 2; i++){
        buffer.append(' ');
      }
      buffer.append("<empty list>\n");
    }

  }

  public static void checkTreeStructure(TreeElement anyElement) {
    TreeElement root = anyElement;
    while(root.getTreeParent() != null){
      root = root.getTreeParent();
    }
    if (root instanceof CompositeElement) {
      synchronized (PsiLock.LOCK) {
        checkSubtree((CompositeElement)root);
      }
    }
  }

  private static void checkSubtree(CompositeElement root) {
    if (root.firstChild == null){
      if (root.lastChild != null){
        throw new IncorrectTreeStructureException(root, "firstChild == null, but lastChild != null");
      }
    }
    else{
      for(TreeElement child = root.firstChild; child != null; child = child.getTreeNext()){
        if (child instanceof CompositeElement){
          checkSubtree((CompositeElement)child);
        }
        if (child.getTreeParent() != root){
          throw new IncorrectTreeStructureException(child, "child has wrong parent value");
        }
        if (child == root.firstChild){
          if (child.getTreePrev() != null){
            throw new IncorrectTreeStructureException(root, "firstChild.prev != null");
          }
        }
        else{
          if (child.getTreePrev() == null){
            throw new IncorrectTreeStructureException(child, "not first child has prev == null");
          }
          if (child.getTreePrev().getTreeNext() != child){
            throw new IncorrectTreeStructureException(child, "element.prev.next != element");
          }
        }
        if (child.getTreeNext() == null){
          if (root.lastChild != child){
            throw new IncorrectTreeStructureException(child, "not last child has next == null");
          }
        }
      }
    }
  }

  public static void checkParentChildConsistent(TreeElement element) {
    CompositeElement treeParent = element.getTreeParent();
    if (treeParent == null) return;
    TreeElement[] elements = treeParent.getChildren(null);
    if (ArrayUtil.find(elements, element) == -1) {
      throw new IncorrectTreeStructureException(element, "child cannot be found among parents children");
    }
    //LOG.debug("checked consistence: "+System.identityHashCode(element));
  }

  public static void checkSameCharTabs(TreeElement element1, TreeElement element2) {
    final CharTable fromCharTab = SharedImplUtil.findCharTableByTree(element1);
    final CharTable toCharTab = SharedImplUtil.findCharTableByTree(element2);
    LOG.assertTrue(fromCharTab == toCharTab);
  }

  public static class IncorrectTreeStructureException extends RuntimeException {
    private final TreeElement myElement;

    public IncorrectTreeStructureException(TreeElement element, String message) {
      super(message);
      myElement = element;
    }

    public TreeElement getElement() {
      return myElement;
    }
  }
}
