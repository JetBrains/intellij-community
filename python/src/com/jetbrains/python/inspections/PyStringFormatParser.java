package com.jetbrains.python.inspections;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyStringFormatParser {
  public static abstract class FormatStringChunk {
    private final int myStartIndex;
    protected int myEndIndex;

    public FormatStringChunk(int startIndex, int endIndex) {
      myStartIndex = startIndex;
      myEndIndex = endIndex;
    }

    public int getStartIndex() {
      return myStartIndex;
    }

    public int getEndIndex() {
      return myEndIndex;
    }
  }

  public static class ConstantChunk extends FormatStringChunk {

    public ConstantChunk(int startIndex, int endIndex) {
      super(startIndex, endIndex);
    }
  }

  public static class SubstitutionChunk extends FormatStringChunk {
    private String myMappingKey;
    private String myConversionFlags;
    private String myWidth;
    private String myPrecision;
    private char myLengthModifier;
    private char myConversionType;

    public SubstitutionChunk(int startIndex) {
      super(startIndex, startIndex);
    }

    public void setEndIndex(int endIndex) {
      myEndIndex = endIndex;
    }

    public char getConversionType() {
      return myConversionType;
    }

    public void setConversionType(char conversionType) {
      myConversionType = conversionType;
    }

    public String getMappingKey() {
      return myMappingKey;
    }

    public void setMappingKey(String mappingKey) {
      myMappingKey = mappingKey;
    }

    public String getConversionFlags() {
      return myConversionFlags;
    }

    public void setConversionFlags(String conversionFlags) {
      myConversionFlags = conversionFlags;
    }

    public String getWidth() {
      return myWidth;
    }

    public void setWidth(String width) {
      myWidth = width;
    }

    public String getPrecision() {
      return myPrecision;
    }

    public void setPrecision(String precision) {
      myPrecision = precision;
    }

    public char getLengthModifier() {
      return myLengthModifier;
    }

    public void setLengthModifier(char lengthModifier) {
      myLengthModifier = lengthModifier;
    }
  }

  private final String myLiteral;
  private final List<FormatStringChunk> myResult = new ArrayList<FormatStringChunk>();
  private int myPos;

  private static final String CONVERSION_FLAGS = "#0- +";
  private static final String DIGITS_OR_ASTERISK = "0123456789*";
  private static final String LENGTH_MODIFIERS = "hlL*";

  public PyStringFormatParser(String literal) {
    myLiteral = literal;
  }

  public List<FormatStringChunk> parse() {
    myPos = 0;
    while(myPos < myLiteral.length()) {
      int next = myLiteral.indexOf('%', myPos);
      while(next >= 0 && next < myLiteral.length()-1 && myLiteral.charAt(next+1) == '%') {
        next = myLiteral.indexOf('%', next+2);
      }
      if (next < 0) break;
      if (next > myPos) {
        myResult.add(new ConstantChunk(myPos, next));
      }
      myPos = next;
      parseSubstitution();
    }
    if (myPos < myLiteral.length()) {
      myResult.add(new ConstantChunk(myPos, myLiteral.length()));
    }
    return myResult;
  }

  private void parseSubstitution() {
    assert myLiteral.charAt(myPos) == '%';
    SubstitutionChunk chunk = new SubstitutionChunk(myPos);
    myResult.add(chunk);
    myPos++;
    if (myPos < myLiteral.length() && myLiteral.charAt(myPos) == '(') {
      int mappingEnd = myLiteral.indexOf(')', myPos+1);
      if (mappingEnd < 0) {
        chunk.setEndIndex(myLiteral.length());
        myPos = myLiteral.length();
        return;
      }
      chunk.setMappingKey(myLiteral.substring(myPos+1, mappingEnd));
      myPos = mappingEnd+1;
    }
    chunk.setConversionFlags(parseWhileCharacterInSet(CONVERSION_FLAGS));
    chunk.setWidth(parseWhileCharacterInSet(DIGITS_OR_ASTERISK));
    if (myPos < myLiteral.length() && myLiteral.charAt(myPos) == '.') {
      myPos++;
      chunk.setPrecision(parseWhileCharacterInSet(DIGITS_OR_ASTERISK));
    }
    if (myPos < myLiteral.length() && LENGTH_MODIFIERS.indexOf(myLiteral.charAt(myPos)) >= 0) {
      chunk.setLengthModifier(myLiteral.charAt(myPos));
      myPos++;
    }
    chunk.setConversionType(myLiteral.charAt(myPos));
    myPos++;
    chunk.setEndIndex(myPos);
  }

  private String parseWhileCharacterInSet(final String characterSet) {
    int flagStart = myPos;
    while(myPos < myLiteral.length() && characterSet.indexOf(myLiteral.charAt(myPos)) >= 0) {
      myPos++;
    }
    return myLiteral.substring(flagStart, myPos);
  }

  public List<SubstitutionChunk> parseSubstitutions() {
    List<SubstitutionChunk> result = new ArrayList<SubstitutionChunk>();
    for (FormatStringChunk chunk : parse()) {
      if (chunk instanceof SubstitutionChunk) {
        result.add((SubstitutionChunk) chunk);
      }
    }
    return result;
  }
}
