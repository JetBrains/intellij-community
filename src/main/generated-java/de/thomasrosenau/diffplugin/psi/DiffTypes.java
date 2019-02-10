// This is a generated file. Not intended for manual editing.
package de.thomasrosenau.diffplugin.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import de.thomasrosenau.diffplugin.psi.impl.*;

public interface DiffTypes {

  IElementType LINE = new DiffElementType("LINE");

  IElementType ADDED = new DiffTokenType("ADDED");
  IElementType COMMAND = new DiffTokenType("COMMAND");
  IElementType DELETED = new DiffTokenType("DELETED");
  IElementType EOLHINT = new DiffTokenType("EOLHINT");
  IElementType FILE = new DiffTokenType("FILE");
  IElementType HUNK_HEAD = new DiffTokenType("HUNK_HEAD");
  IElementType MODIFIED = new DiffTokenType("MODIFIED");
  IElementType OTHER = new DiffTokenType("OTHER");
  IElementType SEPARATOR = new DiffTokenType("SEPARATOR");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == LINE) {
        return new DiffLineImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
