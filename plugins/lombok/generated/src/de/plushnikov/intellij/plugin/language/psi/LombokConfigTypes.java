// This is a generated file. Not intended for manual editing.
package de.plushnikov.intellij.plugin.language.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import de.plushnikov.intellij.plugin.language.psi.impl.*;

public interface LombokConfigTypes {

  IElementType CLEANER = new LombokConfigElementType("CLEANER");
  IElementType OPERATION = new LombokConfigElementType("OPERATION");
  IElementType PROPERTY = new LombokConfigElementType("PROPERTY");

  IElementType CLEAR = new LombokConfigTokenType("CLEAR");
  IElementType COMMENT = new LombokConfigTokenType("COMMENT");
  IElementType CRLF = new LombokConfigTokenType("CRLF");
  IElementType KEY = new LombokConfigTokenType("KEY");
  IElementType SEPARATOR = new LombokConfigTokenType("SEPARATOR");
  IElementType SIGN = new LombokConfigTokenType("SIGN");
  IElementType VALUE = new LombokConfigTokenType("VALUE");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == CLEANER) {
        return new LombokConfigCleanerImpl(node);
      }
      else if (type == OPERATION) {
        return new LombokConfigOperationImpl(node);
      }
      else if (type == PROPERTY) {
        return new LombokConfigPropertyImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
