package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.CompositePsiElement;

public class PsiLiteralExpressionImpl extends CompositePsiElement implements PsiLiteralExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl");


  public PsiLiteralExpressionImpl() {
    super(LITERAL_EXPRESSION);
  }

  public PsiType getType() {
    IElementType i = firstChild.getElementType();
    if (i == INTEGER_LITERAL) {
      return PsiType.INT;
    }
    else if (i == LONG_LITERAL) {
      return PsiType.LONG;
    }
    else if (i == FLOAT_LITERAL) {
      return PsiType.FLOAT;
    }
    else if (i == DOUBLE_LITERAL) {
      return PsiType.DOUBLE;
    }
    else if (i == CHARACTER_LITERAL) {
      return PsiType.CHAR;
    }
    else if (i == STRING_LITERAL) {
      return PsiType.getJavaLangString(getManager());
    }
    else if (i == TRUE_KEYWORD || i == FALSE_KEYWORD) {
      return PsiType.BOOLEAN;
    }
    else if (i == NULL_KEYWORD) {
      return PsiType.NULL;
    }
    else {
      LOG.assertTrue(false);
      return null;
    }
  }

  private static final String _2_IN_63 = Long.toString(-1L << 63).substring(1);
  private static final String _2_IN_31 = Long.toString(-1L << 31).substring(1);
  private static final String _2_IN_63_L = _2_IN_63 + "l";

  public Object getValue() {
    String text = firstChild.getText();
    int textLength = text.length();
    IElementType i = firstChild.getElementType();
    if (i == INTEGER_LITERAL) {
      try {
        if (text.startsWith("0x") || text.startsWith("0X")) {
          // should fit in 32 bits
          if (textLength <= 9) return Integer.valueOf(text.substring(2), 16);
          final Long value = parseDigits(text.substring(2), 4, 32);
          return value == null ? null : new Integer(value.intValue());
        }
        else if (StringUtil.startsWithChar(text, '0')) {
          // should fit in 32 bits
          if (textLength <= 12) return Integer.valueOf(text, 8);
          final Long value = parseDigits(text, 3, 32);
          return value == null ? null : new Integer(value.intValue());
        }
        else {
          final long l = Long.parseLong(text, 10);
          if (text.equals(_2_IN_31)) return new Integer((int)l);
          long converted = (int)l;
          return l == converted ? new Integer((int)l) : null;
        }
      }
      catch (Exception e) {
        return null;
      }
    }
    else if (i == LONG_LITERAL) {
      if (StringUtil.endsWithChar(text, 'L') || StringUtil.endsWithChar(text, 'l')) {
        text = text.substring(0, textLength - 1);
        textLength = text.length();
      }
      try {
        if (text.startsWith("0x") || text.startsWith("0X")) {
          if (textLength <= 17) return Long.valueOf(text.substring(2), 16);
          return parseDigits(text.substring(2), 4, 64);
        }
        else if (StringUtil.startsWithChar(text, '0')) {
          // should fit in 64 bits
          if (textLength <= 23) return Long.valueOf(text, 8);
          return parseDigits(text, 3, 64);
        }
        else {
          if (_2_IN_63.equals(text)) return new Long(-1L << 63);
          return Long.valueOf(text, 10);
        }
      }
      catch (Exception e) {
        return null;
      }
    }
    else if (i == FLOAT_LITERAL) {
      try {
        return Float.valueOf(text);
      }
      catch (Exception e) {
        return null;
      }
    }
    else if (i == DOUBLE_LITERAL) {
      try {
        return Double.valueOf(text);
      }
      catch (Exception e) {
        return null;
      }
    }
    else if (i == CHARACTER_LITERAL) {
      if (StringUtil.endsWithChar(text, '\'')) {
        if (textLength == 1) return null;
        text = text.substring(1, textLength - 1);
      }
      else {
        text = text.substring(1, textLength);
      }
      String s = parseStringCharacters(text);
      if (s == null) return null;
      if (s.length() != 1) return null;
      return new Character(s.charAt(0));
    }
    else if (i == STRING_LITERAL) {
      if (StringUtil.endsWithChar(text, '\"')) {
        if (textLength == 1) return null;
        text = text.substring(1, textLength - 1);
      }
      else {
//          text = text.substring(1, textLength);
        return null;
      }
      return parseStringCharacters(text);
    }
    else if (i == TRUE_KEYWORD) {
      return Boolean.TRUE;
    }
    else if (i == FALSE_KEYWORD) {
      return Boolean.FALSE;
    }
    else if (i == NULL_KEYWORD) {
      return null;
    }
    else {
      LOG.assertTrue(false);
      return null;
    }
  }

  // convert text to number according to radix specified
  // if number is more than maxbits bits long, return null
  private static Long parseDigits(String text, int bitsInRadix, int maxBits) {
    final int radix = 1 << bitsInRadix;
    int textLength = text.length();
    long integer = Long.parseLong(text.substring(0, textLength - 1), radix);
    final int lastDigit = Integer.parseInt("" + text.charAt(textLength - 1), radix);
    if ((integer & (-1L << maxBits - 4)) != 0) return null;
    integer <<= bitsInRadix;
    integer |= lastDigit;
    return new Long(integer);
  }

  public String getParsingError() {
    final Object value = getValue();
    String text = firstChild.getText();
    IElementType i = firstChild.getElementType();
    if (i == INTEGER_LITERAL) {
      text = text.toLowerCase();
      //literal 2147483648 may appear only as the operand of the unary negation operator -.
      if (!(text.equals(_2_IN_31)
            && getParent() instanceof PsiPrefixExpression
            && ((PsiPrefixExpression)getParent()).getOperationSign().getTokenType() == JavaTokenType.MINUS)) {
        if (text.equals("0x")) return "Hexadecimal numbers must contain at least one hexadecimal digit";
        if (value == null || text.equals(_2_IN_31)) {
          return "Integer number too large";
        }
      }
    }
    else if (i == LONG_LITERAL) {
      text = text.toLowerCase();
      //literal 9223372036854775808L may appear only as the operand of the unary negation operator -.
      if (!(text.equals(_2_IN_63_L)
            && getParent() instanceof PsiPrefixExpression
            && ((PsiPrefixExpression)getParent()).getOperationSign().getTokenType() == JavaTokenType.MINUS)) {
        if (text.equals("0x") || text.equals("0xl")) return "Hexadecimal numbers must contain at least one hexadecimal digit";
        if (value == null || text.equals(_2_IN_63_L)) {
          return "Long number too large";
        }
      }
    }
    else if (i == FLOAT_LITERAL || i == DOUBLE_LITERAL) {
      if (value == null) {
        return "Malformed floating point literal";
      }
    }
    else if (i == TRUE_KEYWORD || i == FALSE_KEYWORD || i == NULL_KEYWORD) {
      // TODO
    }
    else if (i == CHARACTER_LITERAL) {
      if (value == null) {
        if (StringUtil.endsWithChar(text, '\'')) {
          if (text.length() == 1) return "Illegal line end in character literal";
          text = text.substring(1, text.length() - 1);
        }
        else {
          return "Illegal line end in character literal";
        }
        String s = parseStringCharacters(text);
        if (s == null) return "Illegal escape character in character literal";
        if (s.length() > 1) {
          return "Too many characters in character literal";
        }
        else if (s.length() == 0) return "Empty character literal";
      }
    }
    else if (i == STRING_LITERAL) {
      if (value == null) {
        if (StringUtil.endsWithChar(text, '\"')) {
          if (text.length() == 1) return "Illegal line end in string literal";
          text = text.substring(1, text.length() - 1);
        }
        else {
          return "Illegal line end in string literal";
        }
        if (parseStringCharacters(text) == null) return "Illegal escape character in string literal";
      }
    }

    if (value instanceof Float) {
      final Float number = (Float)value;
      if (number.isInfinite()) return "Floating point number too large";
      if (number.floatValue() == 0 && !isFPZero()) return "Floating point number too small";
    }
    if (value instanceof Double) {
      final Double number = (Double)value;
      if (number.isInfinite()) return "Floating point number too large";
      if (number.doubleValue() == 0 && !isFPZero()) return "Floating point number too small";
    }
    return null;
  }

  /**
   * @return true if floating point literal consists of zeros only
   */
  private boolean isFPZero() {
    String text = firstChild.getText();
    for(int i = 0; i < text.length(); i++){
      char c = text.charAt(i);
      if (Character.isDigit(c) && c != '0') return false;
      if (Character.toUpperCase(c) == 'E') break;
    }
    return true;
  }

  private static String parseStringCharacters(String chars) {
    if (chars.indexOf('\\') < 0) return chars.intern();
    StringBuffer buffer = new StringBuffer(chars.length());
    int index = 0;
    while(index < chars.length()){
      char c = chars.charAt(index++);
      if (c != '\\'){
        buffer.append(c);
      }
      else{
        if (index == chars.length()) return null;
        c = chars.charAt(index++);
        switch(c){
          case 'b':
            buffer.append('\b');
            break;

          case 't':
            buffer.append('\t');
            break;

          case 'n':
            buffer.append('\n');
            break;

          case 'f':
            buffer.append('\f');
            break;

          case 'r':
            buffer.append('\r');
            break;

          case '"':
            buffer.append('"');
            break;

          case '\'':
            buffer.append('\'');
            break;

          case '\\':
            buffer.append('\\');
            break;

          case '0':
          case '1':
          case '2':
          case '3':
          case '4':
          case '5':
          case '6':
          case '7':
            {
              char startC = c;
              int v = (int)c - '0';
              if (index < chars.length()){
                c = chars.charAt(index++);
                if ('0' <= c && c <= '7'){
                  v <<= 3;
                  v += c - '0';
                  if (startC <= '3' && index < chars.length()){
                    c = chars.charAt(index++);
                    if ('0' <= c && c <= '7'){
                      v <<= 3;
                      v += c - '0';
                    }
                    else{
                      index--;
                    }
                  }
                }
                else{
                  index--;
                }
              }
              buffer.append((char)v);
            }
            break;

          case 'u':
            if (index + 4 <= chars.length()){
              try{
                int v = Integer.parseInt(chars.substring(index, index + 4), 16);
                //line separators are invalid here
                if (v == 0x000a || v == 0x000d) return null;
                c = chars.charAt(index);
                if (c == '+' || c == '-'){
                  return null;
                }
                buffer.append((char)v);
                index += 4;
              }
              catch(Exception e){
                return null;
              }
            }
            else{
              return null;
            }
            break;

          default:
            return null;
        }
      }
    }
    return buffer.toString().intern();
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitLiteralExpression(this);
  }

  public String toString() {
    return "PsiLiteralExpression:" + getText();
  }
}

