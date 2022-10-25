// This is a generated file. Not intended for manual editing.
package com.jetbrains.performancePlugin.lang.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.jetbrains.performancePlugin.lang.psi.impl.*;

public interface IJPerfElementTypes {

  IElementType COMMAND_LINE = new IJPerfElementType("COMMAND_LINE");
  IElementType COMMAND_NAME = new IJPerfElementType("COMMAND_NAME");
  IElementType DELAY_TYPING_OPTION = new IJPerfElementType("DELAY_TYPING_OPTION");
  IElementType GOTO_OPTION = new IJPerfElementType("GOTO_OPTION");
  IElementType OPTION = new IJPerfElementType("OPTION");
  IElementType OPTION_LIST = new IJPerfElementType("OPTION_LIST");
  IElementType SIMPLE_OPTION = new IJPerfElementType("SIMPLE_OPTION");
  IElementType STATEMENT = new IJPerfElementType("STATEMENT");

  IElementType ASSIGNMENT_OPERATOR = new IJPerfTokenType("ASSIGNMENT_OPERATOR");
  IElementType COMMAND = new IJPerfTokenType("COMMAND");
  IElementType COMMENT = new IJPerfTokenType("COMMENT");
  IElementType FILE_PATH = new IJPerfTokenType("FILE_PATH");
  IElementType IDENTIFIER = new IJPerfTokenType("IDENTIFIER");
  IElementType NUMBER = new IJPerfTokenType("NUMBER");
  IElementType OPTIONS_SEPARATOR = new IJPerfTokenType("OPTIONS_SEPARATOR");
  IElementType PIPE = new IJPerfTokenType("PIPE");
  IElementType TEXT = new IJPerfTokenType("TEXT");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == COMMAND_LINE) {
        return new IJPerfCommandLineImpl(node);
      }
      else if (type == COMMAND_NAME) {
        return new IJPerfCommandNameImpl(node);
      }
      else if (type == DELAY_TYPING_OPTION) {
        return new IJPerfDelayTypingOptionImpl(node);
      }
      else if (type == GOTO_OPTION) {
        return new IJPerfGotoOptionImpl(node);
      }
      else if (type == OPTION) {
        return new IJPerfOptionImpl(node);
      }
      else if (type == OPTION_LIST) {
        return new IJPerfOptionListImpl(node);
      }
      else if (type == SIMPLE_OPTION) {
        return new IJPerfSimpleOptionImpl(node);
      }
      else if (type == STATEMENT) {
        return new IJPerfStatementImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
