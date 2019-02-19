// This is a generated file. Not intended for manual editing.
package de.thomasrosenau.diffplugin.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import de.thomasrosenau.diffplugin.psi.impl.*;

public interface DiffTypes {

  IElementType ANY_LINE = new DiffElementType("ANY_LINE");
  IElementType CONSOLE_COMMAND = new DiffElementType("CONSOLE_COMMAND");
  IElementType CONTEXT_DIFF = new DiffElementType("CONTEXT_DIFF");
  IElementType CONTEXT_FROM_FILE_LINE = new DiffElementType("CONTEXT_FROM_FILE_LINE");
  IElementType CONTEXT_HUNK = new DiffElementType("CONTEXT_HUNK");
  IElementType CONTEXT_HUNK_FROM = new DiffElementType("CONTEXT_HUNK_FROM");
  IElementType CONTEXT_HUNK_TO = new DiffElementType("CONTEXT_HUNK_TO");
  IElementType CONTEXT_TO_FILE_LINE = new DiffElementType("CONTEXT_TO_FILE_LINE");
  IElementType LEADING_TEXT = new DiffElementType("LEADING_TEXT");
  IElementType NORMAL_DIFF = new DiffElementType("NORMAL_DIFF");
  IElementType NORMAL_HUNK = new DiffElementType("NORMAL_HUNK");
  IElementType NORMAL_HUNK_ADD = new DiffElementType("NORMAL_HUNK_ADD");
  IElementType NORMAL_HUNK_CHANGE = new DiffElementType("NORMAL_HUNK_CHANGE");
  IElementType NORMAL_HUNK_DELETE = new DiffElementType("NORMAL_HUNK_DELETE");
  IElementType TRAILING_TEXT = new DiffElementType("TRAILING_TEXT");
  IElementType UNIFIED_DIFF = new DiffElementType("UNIFIED_DIFF");
  IElementType UNIFIED_HUNK = new DiffElementType("UNIFIED_HUNK");
  IElementType UNIFIED_LINE = new DiffElementType("UNIFIED_LINE");

  IElementType COMMAND = new DiffTokenType("COMMAND");
  IElementType CONTEXT_CHANGED_LINE = new DiffTokenType("CONTEXT_CHANGED_LINE");
  IElementType CONTEXT_COMMON_LINE = new DiffTokenType("CONTEXT_COMMON_LINE");
  IElementType CONTEXT_DELETED_LINE = new DiffTokenType("CONTEXT_DELETED_LINE");
  IElementType CONTEXT_FROM_LABEL = new DiffTokenType("CONTEXT_FROM_LABEL");
  IElementType CONTEXT_FROM_LINE_NUMBERS = new DiffTokenType("CONTEXT_FROM_LINE_NUMBERS");
  IElementType CONTEXT_HUNK_SEPARATOR = new DiffTokenType("CONTEXT_HUNK_SEPARATOR");
  IElementType CONTEXT_INSERTED_LINE = new DiffTokenType("CONTEXT_INSERTED_LINE");
  IElementType CONTEXT_TO_LABEL = new DiffTokenType("CONTEXT_TO_LABEL");
  IElementType CONTEXT_TO_LINE_NUMBERS = new DiffTokenType("CONTEXT_TO_LINE_NUMBERS");
  IElementType EOL_HINT = new DiffTokenType("EOL_HINT");
  IElementType NORMAL_ADD_COMMAND = new DiffTokenType("NORMAL_ADD_COMMAND");
  IElementType NORMAL_CHANGE_COMMAND = new DiffTokenType("NORMAL_CHANGE_COMMAND");
  IElementType NORMAL_DELETE_COMMAND = new DiffTokenType("NORMAL_DELETE_COMMAND");
  IElementType NORMAL_FROM_LINE = new DiffTokenType("NORMAL_FROM_LINE");
  IElementType NORMAL_SEPARATOR = new DiffTokenType("NORMAL_SEPARATOR");
  IElementType NORMAL_TO_LINE = new DiffTokenType("NORMAL_TO_LINE");
  IElementType OTHER = new DiffTokenType("OTHER");
  IElementType UNIFIED_COMMON_LINE = new DiffTokenType("UNIFIED_COMMON_LINE");
  IElementType UNIFIED_DELETED_LINE = new DiffTokenType("UNIFIED_DELETED_LINE");
  IElementType UNIFIED_FROM_LABEL = new DiffTokenType("UNIFIED_FROM_LABEL");
  IElementType UNIFIED_INSERTED_LINE = new DiffTokenType("UNIFIED_INSERTED_LINE");
  IElementType UNIFIED_LINE_NUMBERS = new DiffTokenType("UNIFIED_LINE_NUMBERS");
  IElementType UNIFIED_TO_LABEL = new DiffTokenType("UNIFIED_TO_LABEL");
  IElementType WHITE_SPACE = new DiffTokenType("WHITE_SPACE");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == ANY_LINE) {
        return new DiffAnyLineImpl(node);
      }
      else if (type == CONSOLE_COMMAND) {
        return new DiffConsoleCommandImpl(node);
      }
      else if (type == CONTEXT_DIFF) {
        return new DiffContextDiffImpl(node);
      }
      else if (type == CONTEXT_FROM_FILE_LINE) {
        return new DiffContextFromFileLineImpl(node);
      }
      else if (type == CONTEXT_HUNK) {
        return new DiffContextHunkImpl(node);
      }
      else if (type == CONTEXT_HUNK_FROM) {
        return new DiffContextHunkFromImpl(node);
      }
      else if (type == CONTEXT_HUNK_TO) {
        return new DiffContextHunkToImpl(node);
      }
      else if (type == CONTEXT_TO_FILE_LINE) {
        return new DiffContextToFileLineImpl(node);
      }
      else if (type == LEADING_TEXT) {
        return new DiffLeadingTextImpl(node);
      }
      else if (type == NORMAL_DIFF) {
        return new DiffNormalDiffImpl(node);
      }
      else if (type == NORMAL_HUNK) {
        return new DiffNormalHunkImpl(node);
      }
      else if (type == NORMAL_HUNK_ADD) {
        return new DiffNormalHunkAddImpl(node);
      }
      else if (type == NORMAL_HUNK_CHANGE) {
        return new DiffNormalHunkChangeImpl(node);
      }
      else if (type == NORMAL_HUNK_DELETE) {
        return new DiffNormalHunkDeleteImpl(node);
      }
      else if (type == TRAILING_TEXT) {
        return new DiffTrailingTextImpl(node);
      }
      else if (type == UNIFIED_DIFF) {
        return new DiffUnifiedDiffImpl(node);
      }
      else if (type == UNIFIED_HUNK) {
        return new DiffUnifiedHunkImpl(node);
      }
      else if (type == UNIFIED_LINE) {
        return new DiffUnifiedLineImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
