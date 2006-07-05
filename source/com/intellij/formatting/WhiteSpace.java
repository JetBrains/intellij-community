package com.intellij.formatting;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;

import java.util.ArrayList;

class WhiteSpace {


  private TextRange myTextRange;

  private int mySpaces;
  private int myIndentSpaces;
  private int myLineFeeds;

  private int myInitialLineFeeds;
  private int myInitialSpaces = 0;
  private CharSequence myInitial;

  private int myFlags;

  private final static byte SAFE = 0x2;
  private final static byte KEEP_FIRST_COLUMN = 0x4;
  private final static byte FIRST = 0x40;
  private final static byte LINE_FEEDS_ARE_READ_ONLY = 0x8;
  private final static byte READ_ONLY = 0x10;

  public WhiteSpace(int startOffset, boolean isFirst) {
    myTextRange = new TextRange(startOffset, startOffset);
    mySpaces = 0;
    myIndentSpaces = 0;
    myLineFeeds = 0;
    myInitialLineFeeds = 0;
    setIsFirstWhiteSpace(isFirst);
  }

  public void append(int newEndOffset, FormattingDocumentModel model, CodeStyleSettings.IndentOptions options) {
    final int oldEndOffset = myTextRange.getEndOffset();
    if (newEndOffset == oldEndOffset) return;
    if (myTextRange.getStartOffset() >= newEndOffset) {
      InitialInfoBuilder.assertInvalidRanges(
        myTextRange.getStartOffset(),
        newEndOffset,
        model,
        "some block intersects with whitespace"
      );
    }
    myTextRange = new TextRange(myTextRange.getStartOffset(), newEndOffset);
    myInitial = model.getText(myTextRange);
    
    if (!coveredByBlock(model)) {
      InitialInfoBuilder.assertInvalidRanges(
        myTextRange.getStartOffset(),
        myTextRange.getEndOffset(),
        model,
        "nonempty text is not covered by block"
      );
    }

    final int tabsize = options.TAB_SIZE;
    for (int i = oldEndOffset - myTextRange.getStartOffset(); i < newEndOffset - myTextRange.getStartOffset(); i++) {
      switch (myInitial.charAt(i)) {
        case '\n':
          myLineFeeds++;
          mySpaces = 0;
          myIndentSpaces = 0;
          break;
        case ' ':
          mySpaces++;
          break;
        case '\t':
          myIndentSpaces += tabsize;
          break;
      }
    }

    myInitialLineFeeds = myLineFeeds;
    myInitialSpaces = getTotalSpaces();
  }

  private boolean coveredByBlock(final FormattingDocumentModel model) {
    if (myInitial == null) return true;
    if (myInitial.toString().trim().length() == 0) return true;
    if (!(model instanceof FormattingDocumentModelImpl)) return false;
    PsiFile psiFile = ((FormattingDocumentModelImpl)model).getFile();
    if (psiFile == null) return false;
    PsiElement start = psiFile.findElementAt(myTextRange.getStartOffset());
    PsiElement end = psiFile.findElementAt(myTextRange.getEndOffset()-1);
    return start == end && start instanceof PsiWhiteSpace; // there maybe non-white text inside CDATA-encoded elements
  }

  public String generateWhiteSpace(CodeStyleSettings.IndentOptions options) {

    StringBuffer buffer = new StringBuffer();
    StringUtil.repeatSymbol(buffer, '\n', myLineFeeds);

    repeatTrailingSymbols(options, buffer, myIndentSpaces, mySpaces);

    return buffer.toString();

  }

  private static void repeatTrailingSymbols(final CodeStyleSettings.IndentOptions options,
                                     final StringBuffer buffer,
                                     final int indentSpaces,
                                     final int spaces) {
    if (options.USE_TAB_CHARACTER) {
      if (options.SMART_TABS) {
        int tabCount = indentSpaces / options.TAB_SIZE;
        int leftSpaces = indentSpaces - tabCount * options.TAB_SIZE;
        StringUtil.repeatSymbol(buffer, '\t', tabCount);
        StringUtil.repeatSymbol(buffer, ' ', leftSpaces + spaces);
      }
      else {
        int size = spaces + indentSpaces;
        while (size > 0) {
          if (size >= options.TAB_SIZE) {
            buffer.append('\t');
            size -= options.TAB_SIZE;
          }
          else {
            buffer.append(' ');
            size--;
          }
        }
      }
    }
    else {
      StringUtil.repeatSymbol(buffer, ' ', spaces + indentSpaces);
    }
  }

  public void setSpaces(final int spaces, final int indent) {
    performModification(new Runnable() {
      public void run() {
        if (!isKeepFirstColumn() || myInitialSpaces > 0) {
          mySpaces = spaces;
          myIndentSpaces = indent;
        }
      }
    });
  }

  private boolean doesNotContainAnySpaces() {
    return getTotalSpaces() == 0 && myLineFeeds == 0;
  }

  public TextRange getTextRange() {
    return myTextRange;
  }

  private void performModification(Runnable action) {
    if (isIsReadOnly()) return;
    final boolean before = doesNotContainAnySpaces();
    final int lineFeedsBefore = getLineFeeds();
    action.run();
    if (isLineFeedsAreReadOnly()) {
      myLineFeeds = lineFeedsBefore;
    }
    if (isIsSafe()) {
      final boolean after = doesNotContainAnySpaces();
      if (before && !after) {
        mySpaces = 0;
        myIndentSpaces = 0;
        myLineFeeds = 0;
      }
      else if (!before && after) {
        mySpaces = 1;
        myIndentSpaces = 0;
      }
    }
  }

  public void arrangeSpaces(final SpacingImpl spaceProperty) {
    performModification(new Runnable() {
      public void run() {
        if (spaceProperty != null) {
          if (myLineFeeds == 0) {
            if (getTotalSpaces() < spaceProperty.getMinSpaces()) {
              setSpaces(spaceProperty.getMinSpaces(), 0);
            }
            if (getTotalSpaces() > spaceProperty.getMaxSpaces()) {
              setSpaces(spaceProperty.getMaxSpaces(), 0);
            }
          }
        }
      }
    });


  }

  public void arrangeLineFeeds(final SpacingImpl spaceProperty, final FormatProcessor formatProcessor) {
    performModification(new Runnable() {
      public void run() {
        if (spaceProperty != null) {
          spaceProperty.refresh(formatProcessor);

          if (spaceProperty.getMinLineFeeds() >= 0 && myLineFeeds < spaceProperty.getMinLineFeeds()) {
            myLineFeeds = spaceProperty.getMinLineFeeds();
          }
          if (myLineFeeds > 0) {
            if (spaceProperty.getKeepBlankLines() > 0) {
              if (myLineFeeds >= spaceProperty.getKeepBlankLines() + 1) {
                myLineFeeds = spaceProperty.getKeepBlankLines() + 1;
              }
            }
            else {
              if (myLineFeeds > spaceProperty.getMinLineFeeds()) {
                if (spaceProperty.shouldKeepLineFeeds()) {
                  myLineFeeds = Math.max(spaceProperty.getMinLineFeeds(), 1);
                }
                else {
                  myLineFeeds = spaceProperty.getMinLineFeeds();
                  if (myLineFeeds == 0) mySpaces = 0;
                }
              }
            }
            if (myLineFeeds == 1 && !spaceProperty.shouldKeepLineFeeds() && spaceProperty.getMinLineFeeds() == 0) {
              myLineFeeds = 0;
              mySpaces = 0;
            }
          }
        } else if (isFirst()) {
          myLineFeeds = 0;
          mySpaces = 0;
        }
      }
    });

  }

  public int getLineFeeds() {
    return myLineFeeds;
  }

  public boolean containsLineFeeds() {
    return isIsFirstWhiteSpace() || myLineFeeds > 0;
  }

  public int getTotalSpaces() {
    return mySpaces + myIndentSpaces;
  }

  public void ensureLineFeed() {
    performModification(new Runnable() {
      public void run() {
        if (!containsLineFeeds()) {
          myLineFeeds = 1;
          mySpaces = 0;
        }
      }
    });
  }

  public boolean isReadOnly() {
    return isIsReadOnly() || (isIsSafe() && doesNotContainAnySpaces());
  }

  public boolean equalsToString(String ws) {
    if (myInitial == null) return ws.length() == 0;
    return myInitial.toString().equals(ws);
  }

  public void setIsSafe(final boolean value) {
    setFlag(SAFE, value);
  }

  private void setFlag(final int mask, final boolean value) {
    if (value) {
      myFlags |= mask;
    }
    else {
      myFlags &= ~mask;
    }
  }

  private boolean getFlag(final int mask) {
    return (myFlags & mask) != 0;
  }

  public boolean isFirst() {
    return isIsFirstWhiteSpace();
  }

  public boolean containsLineFeedsInitially() {
    if (myInitial == null) return false;
    return myInitialLineFeeds > 0;
  }

  public void removeLineFeeds(final Spacing spacing, final FormatProcessor formatProcessor) {
    performModification(new Runnable() {
      public void run() {
        myLineFeeds = 0;
        mySpaces = 0;
        myIndentSpaces = 0;
      }
    });
    arrangeLineFeeds((SpacingImpl)spacing, formatProcessor);
    arrangeSpaces((SpacingImpl)spacing);
  }

  public int getIndentOffset() {
    return myIndentSpaces;
  }

  public int getSpaces() {
    return mySpaces;
  }

  public void setKeepFirstColumn(final boolean b) {
    setFlag(KEEP_FIRST_COLUMN, b);
  }

  public void setLineFeedsAreReadOnly() {
    setLineFeedsAreReadOnly(true);
  }

  public void setReadOnly(final boolean isReadOnly) {
    setIsReadOnly(isReadOnly);
  }

  public boolean isIsFirstWhiteSpace() {
    return getFlag(FIRST);
  }

  public boolean isIsSafe() {
    return getFlag(SAFE);
  }

  public boolean isKeepFirstColumn() {
    return getFlag(KEEP_FIRST_COLUMN);
  }

  public boolean isLineFeedsAreReadOnly() {
    return getFlag(LINE_FEEDS_ARE_READ_ONLY);
  }

  public void setLineFeedsAreReadOnly(final boolean lineFeedsAreReadOnly) {
    setFlag(LINE_FEEDS_ARE_READ_ONLY, lineFeedsAreReadOnly);
  }

  public boolean isIsReadOnly() {
    return getFlag(READ_ONLY);
  }

  public void setIsReadOnly(final boolean isReadOnly) {
    setFlag(READ_ONLY, isReadOnly);
  }

  public void setIsFirstWhiteSpace(final boolean isFirstWhiteSpace) {
    setFlag(FIRST, isFirstWhiteSpace);
  }

  public String generateWhiteSpace(final CodeStyleSettings.IndentOptions indentOptions,
                                                  final int offset,
                                                  final IndentInfo indent) {
    final StringBuffer result = new StringBuffer();
    int currentOffset = getTextRange().getStartOffset();
    String[] lines = getInitialLines();
    int currentLine = 0;
    for (int i = 0; i < lines.length - 1 && currentOffset + lines[i].length() <= offset; i++) {
      result.append(lines[i]);
      currentOffset += lines[i].length();
      result.append('\n');
      currentOffset++;
      currentLine++;
      if (currentOffset == offset) {
        break;
      }
      
    }
    final String newIndentSpaces = indent.generateNewWhiteSpace(indentOptions);
    result.append(newIndentSpaces);
    if (currentLine + 1 < lines.length) {
      result.append('\n');
      for (int i = currentLine + 1; i < lines.length - 1; i++) {
        result.append(lines[i]);
        result.append('\n');
      }
      repeatTrailingSymbols(indentOptions, result, myIndentSpaces, mySpaces);
    }
    return result.toString();
  }

/*  
  public String generateWhiteSpace(final CodeStyleSettings.IndentOptions indentOptions,
                                   final int offset,
                                   final IndentInfo indent,
                                   final PsiBasedFormattingModel model) {
    final int modifiedLine = model.getLineNumber(offset);
    int currentLine = modifiedLine;

    final StringBuffer result = new StringBuffer();

    for (int i = currentLine; i < currentLine + myLineFeeds - 1; i++) {
      result.append('\n');
      if (i >= modifiedLine) {
        result.append(indent.generateNewWhiteSpace(indentOptions));
      }
    }
    result.append('\n');

    if (myLineFeeds == 1) {
      result.append(indent.generateNewWhiteSpace(indentOptions));
    } else {
      repeatTrailingSymbols(indentOptions, result);
    }
    return result.toString();
  }
*/

  private String[] getInitialLines() {
    if (myInitial == null) return new String[]{""};
    final ArrayList<String> result = new ArrayList<String>();
    StringBuffer currentLine = new StringBuffer();
    for (int i = 0; i < myInitial.length(); i++) {
      final char c = myInitial.charAt(i);
      if (c == '\n') {
        result.add(currentLine.toString());
        currentLine = new StringBuffer();
      }
      else {
        currentLine.append(c);
      }
    }
    result.add(currentLine.toString());
    return result.toArray(new String[result.size()]);
  }

  public int getIndentSpaces() {
    return myIndentSpaces;
  }
}

