package com.intellij.lexer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class JavaLexer implements Lexer, Cloneable {

  private JavaLexer(boolean isAssertKeywordEnabled, boolean isJDK15, boolean escapeTokensInXml) {
    myTable = isAssertKeywordEnabled ?
              (isJDK15 ? ourTableWithAssertAndJDK15 : ourTableWithAssert) :
              (isJDK15 ? ourTableWithJDK15 : ourTableWithoutAssert);
    myJLexlexer = escapeTokensInXml ?
      (Lexer)new _XmlEscapedJavaLexer(isAssertKeywordEnabled, isJDK15) :
      new _JavaLexer(isAssertKeywordEnabled, isJDK15);
  }

  public JavaLexer(LanguageLevel level, boolean escapeTokensInXml) {
    this(level.hasAssertKeyword(), level.hasEnumKeywordAndAutoboxing(), escapeTokensInXml);
  }

  public JavaLexer(LanguageLevel level) {
    this(level.hasAssertKeyword(), level.hasEnumKeywordAndAutoboxing(), false);
  }

  private char[] myBuffer;
  private int myBufferIndex;
  private int myBufferEndOffset;
  IElementType myTokenType;
  private Lexer myJLexlexer;

  //Positioned after the last symbol of the current token
  private int myTokenEndOffset;

  private static class HashTable {
    static final int NUM_ENTRIES = 9999;
    private static final Logger LOG = Logger.getInstance("com.intellij.Lexer.JavaLexer");

    char[][] myTable = new char[NUM_ENTRIES][];;
    IElementType[] myKeywords = new IElementType[NUM_ENTRIES];

    void add(String s, IElementType tokenType) {
      char[] chars = s.toCharArray();
      int hashCode = chars[0] * 2;
      for (int j = 1; j < chars.length; j++) {
        hashCode += chars[j];
      }
      int modHashCode = hashCode % NUM_ENTRIES;
      LOG.assertTrue(myTable[modHashCode] == null);

      myTable[modHashCode] = chars;
      myKeywords[modHashCode] = tokenType;
    }

    boolean contains(int hashCode, char[] buffer, int offset, int len) {
      int modHashCode = hashCode % NUM_ENTRIES;
      char[] kwd = myTable[modHashCode];
      if (kwd == null) return false;
      for (int j = 0; j < kwd.length; j++) {
        if (buffer[j + offset] != kwd[j]) return false;
      }
      return true;
    }

    IElementType getTokenType(int hashCode) {
      return myKeywords[hashCode % NUM_ENTRIES];
    }

    public HashTable(boolean isAssertKeywordEnabled, boolean isJDK15) {
      if (isAssertKeywordEnabled) {
        add("assert", JavaTokenType.ASSERT_KEYWORD);
      }
      if (isJDK15) {
        add("enum", JavaTokenType.ENUM_KEYWORD);
      }
      add("abstract", JavaTokenType.ABSTRACT_KEYWORD);
      add("default", JavaTokenType.DEFAULT_KEYWORD);
      add("if", JavaTokenType.IF_KEYWORD);
      add("private", JavaTokenType.PRIVATE_KEYWORD);
      add("this", JavaTokenType.THIS_KEYWORD);
      add("boolean", JavaTokenType.BOOLEAN_KEYWORD);
      add("do", JavaTokenType.DO_KEYWORD);
      add("implements", JavaTokenType.IMPLEMENTS_KEYWORD);
      add("protected", JavaTokenType.PROTECTED_KEYWORD);
      add("throw", JavaTokenType.THROW_KEYWORD);
      add("break", JavaTokenType.BREAK_KEYWORD);
      add("double", JavaTokenType.DOUBLE_KEYWORD);
      add("import", JavaTokenType.IMPORT_KEYWORD);
      add("public", JavaTokenType.PUBLIC_KEYWORD);
      add("throws", JavaTokenType.THROWS_KEYWORD);
      add("byte", JavaTokenType.BYTE_KEYWORD);
      add("else", JavaTokenType.ELSE_KEYWORD);
      add("instanceof", JavaTokenType.INSTANCEOF_KEYWORD);
      add("return", JavaTokenType.RETURN_KEYWORD);
      add("transient", JavaTokenType.TRANSIENT_KEYWORD);
      add("case", JavaTokenType.CASE_KEYWORD);
      add("extends", JavaTokenType.EXTENDS_KEYWORD);
      add("int", JavaTokenType.INT_KEYWORD);
      add("short", JavaTokenType.SHORT_KEYWORD);
      add("try", JavaTokenType.TRY_KEYWORD);
      add("catch", JavaTokenType.CATCH_KEYWORD);
      add("final", JavaTokenType.FINAL_KEYWORD);
      add("interface", JavaTokenType.INTERFACE_KEYWORD);
      add("static", JavaTokenType.STATIC_KEYWORD);
      add("void", JavaTokenType.VOID_KEYWORD);
      add("char", JavaTokenType.CHAR_KEYWORD);
      add("finally", JavaTokenType.FINALLY_KEYWORD);
      add("long", JavaTokenType.LONG_KEYWORD);
      add("strictfp", JavaTokenType.STRICTFP_KEYWORD);
      add("volatile", JavaTokenType.VOLATILE_KEYWORD);
      add("class", JavaTokenType.CLASS_KEYWORD);
      add("float", JavaTokenType.FLOAT_KEYWORD);
      add("native", JavaTokenType.NATIVE_KEYWORD);
      add("super", JavaTokenType.SUPER_KEYWORD);
      add("while", JavaTokenType.WHILE_KEYWORD);
      add("const", JavaTokenType.CONST_KEYWORD);
      add("for", JavaTokenType.FOR_KEYWORD);
      add("new", JavaTokenType.NEW_KEYWORD);
      add("switch", JavaTokenType.SWITCH_KEYWORD);
      add("continue", JavaTokenType.CONTINUE_KEYWORD);
      add("goto", JavaTokenType.GOTO_KEYWORD);
      add("package", JavaTokenType.PACKAGE_KEYWORD);
      add("synchronized", JavaTokenType.SYNCHRONIZED_KEYWORD);
      add("true", JavaTokenType.TRUE_KEYWORD);
      add("false", JavaTokenType.FALSE_KEYWORD);
      add("null", JavaTokenType.NULL_KEYWORD);
    }
  }

  private final HashTable myTable;
  private final static HashTable ourTableWithoutAssert = new HashTable(false, false);
  private final static HashTable ourTableWithAssert = new HashTable(true, false);
  private final static HashTable ourTableWithAssertAndJDK15 = new HashTable(true, true);
  private final static HashTable ourTableWithJDK15 = new HashTable(false, true);

  public final void start(char[] buffer) {
    start(buffer, 0, buffer.length);
  }

  public final void start(char[] buffer, int startOffset, int endOffset) {
    myBuffer = buffer;
    myBufferIndex = startOffset;
    myBufferEndOffset = endOffset;
    myTokenType = null;
    myTokenEndOffset = startOffset;
  }

  public final void start(char[] buffer, int startOffset, int endOffset, int initialState) {
    start(buffer, startOffset, endOffset);
  }

  public int getState() {
    return 0;
  }

  public int getLastState() {
    return 0;
  }

  public final IElementType getTokenType() {
    locateToken();

    return myTokenType;
  }

  public final int getTokenStart() {
    return myBufferIndex;
  }

  public final int getTokenEnd() {
    locateToken();
    return myTokenEndOffset;
  }


  public final void advance() {
    locateToken();
    myTokenType = null;
  }

  protected final void locateToken() {
    if (myTokenType != null) return;
    _locateToken();
  }

  public final void _locateToken() {

    if (myTokenEndOffset == myBufferEndOffset) {
      myTokenType = null;
      myBufferIndex = myBufferEndOffset;
      return;
    }

    myBufferIndex = myTokenEndOffset;

    char c = myBuffer[myBufferIndex];
    switch (c) {
      default:
        jlexLocateToken();
        break;

      case ' ':
      case '\t':
      case '\n':
      case '\r':
      case '\f':
        myTokenType = JavaTokenType.WHITE_SPACE;
        myTokenEndOffset = getWhitespaces(myBufferIndex + 1);
        break;

      case '/': {
        if (myBufferIndex + 1 >= myBufferEndOffset) {
          myTokenType = JavaTokenType.DIV;
          myTokenEndOffset = myBufferEndOffset;
        }
        else if (myBuffer[myBufferIndex + 1] == '/') {
          myTokenType = JavaTokenType.END_OF_LINE_COMMENT;
          myTokenEndOffset = getLineTerminator(myBufferIndex + 2);
        }
        else if (myBuffer[myBufferIndex + 1] == '*') {
          if (myBufferIndex + 2 >= myBufferEndOffset || myBuffer[myBufferIndex + 2] != '*') {
            myTokenType = JavaTokenType.C_STYLE_COMMENT;
            myTokenEndOffset = getClosingComment(myBufferIndex + 2);
          }
          else {
            myTokenType = JavaTokenType.DOC_COMMENT;
            myTokenEndOffset = getDocClosingComment(myBufferIndex + 3);
          }
        }
        else if ((c > 127) && Character.isJavaIdentifierStart(c)) {
          myTokenEndOffset = getIdentifier(myBufferIndex + 1);
        }
        else {
          jlexLocateToken();
        }
        break;
      }

      case '"':
      case '\'':
        myTokenType = c == '"' ? JavaTokenType.STRING_LITERAL : JavaTokenType.CHARACTER_LITERAL;
        myTokenEndOffset = getClosingParenthesys(myBufferIndex + 1, c);
    }
    
    if (myTokenEndOffset > myBufferEndOffset) {
      myTokenEndOffset = myBufferEndOffset;
    }
  }

  private int getWhitespaces(int pos) {
    if (pos >= myBufferEndOffset) return myBufferEndOffset;
    char c = myBuffer[pos];

    while (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f') {
      pos++;
      if (pos == myBufferEndOffset) return pos;
      c = myBuffer[pos];
    }

    return pos;
  }

  private void jlexLocateToken() {
    myJLexlexer.start(myBuffer, myBufferIndex, myBufferEndOffset);
    myTokenEndOffset = myJLexlexer.getTokenEnd();
    myTokenType = myJLexlexer.getTokenType();
  }


  private int getClosingParenthesys(int offset, char c) {
    int pos = offset;
    if (pos >= myBufferEndOffset) return myBufferEndOffset;
    char cur = myBuffer[pos];
    while (true) {
      while (cur != c && cur != '\n' && cur != '\r' && cur != '\\') {
        pos++;
        if (pos >= myBufferEndOffset) return myBufferEndOffset;
        cur = myBuffer[pos];
      }

      if (cur == '\\') {
        pos += 2;
        if (pos >= myBufferEndOffset) return myBufferEndOffset;
        cur = myBuffer[pos];
      } else if (cur == c) {
        break;
      } else {
        pos--;
        break;
      }
    }

    return pos + 1;
  }

  private int getDocClosingComment(int offset) {
    if (offset < myBufferEndOffset && myBuffer[offset] == '/') return offset + 1;
    int pos = offset;
    while (pos < myBufferEndOffset - 1 && (myBuffer[pos] != '*' || myBuffer[pos + 1] != '/')) {
      pos++;
    }
    return pos + 2;
  }

  private int getClosingComment(int offset) {
    int pos = offset;

    while (pos < myBufferEndOffset - 1 && (myBuffer[pos] != '*' || myBuffer[pos + 1] != '/')) pos++;

    return pos + 2;
  }

  private int getLineTerminator(int offset) {
    int pos = offset;
    while (pos < myBufferEndOffset && myBuffer[pos] != '\n' && myBuffer[pos] != '\r') {
      pos++;
    }

    return pos;
  }

  private int getIdentifier(int offset) {
    int len = 0;
    int hashCode = myBuffer[offset - 1] * 2;


    if (offset + len < myBufferEndOffset) {
      char c = myBuffer[offset + len];
      while ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
              || c == '_' || c == '$' || ((c > 127) && Character.isJavaIdentifierPart(c))) {
        len++;
        hashCode += c;

        if (offset + len == myBufferEndOffset) break;
        c = myBuffer[offset + len];
      }
    }

    if (myTable.contains(hashCode, myBuffer, offset - 1, len + 1)) {
      myTokenType = myTable.getTokenType(hashCode);
    } else {
      myTokenType = JavaTokenType.IDENTIFIER;
    }

    return offset + len;
  }

  public final char[] getBuffer() {
    return myBuffer;
  }

  public final int getBufferEnd() {
    return myBufferEndOffset;
  }

  public Object clone() {
    try {
      return super.clone();
    } catch (CloneNotSupportedException e) {
      return null;
    }
  }

  public static void main(String[] args) {
    BufferedReader reader;

    try {
      reader = new BufferedReader(new FileReader(args[0]));
      String s;
      StringBuffer buf = new StringBuffer();
      while ((s = reader.readLine()) != null) {
        buf.append(s + "\n");
      }

      char[] cbuf = buf.toString().toCharArray();

      JavaLexer lexer = new JavaLexer(LanguageLevel.JDK_1_5);
      lexer.start(cbuf, 0, cbuf.length);
      while (lexer.getTokenType() != null) {
        lexer.advance();
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();  //To change body of catch statement use Options | File Templates.
    } catch (IOException e) {
      e.printStackTrace();  //To change body of catch statement use Options | File Templates.
    }
  }

  public int getSmartUpdateShift() {
    return 2; // to handle ... correctly
  }
}