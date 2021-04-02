package com.jetbrains.python.lexer;

/**
 * Python lexer kind determines the types of lexical rules allowed for a specific Python dialect.
 * <br/>
 * So far only Python Console and Cython require special lexing rules (and we apply Cython rules by default anyway).
 */
public enum PythonLexerKind {
  REGULAR,  // Regular Python files
  CONSOLE,  // Code fragments for the Python Console
  CYTHON,   // Cython files
}
