package com.intellij.formatting;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import java.util.ArrayList;
import java.util.List;

class LeafBlockWrapper extends AbstractBlockWrapper {

  private static final ArrayList<Block> EMPTY = new ArrayList<Block>();

  private final boolean myContainsLineFeeds;
  private final int mySymbolsAtTheLastLine;
  private final LeafBlockWrapper myPreviousBlock;
  private LeafBlockWrapper myNextBlock;
  private final boolean myIsReadOnly;
  private SpacePropertyImpl mySpaceProperty;
  private final boolean myIsLeaf;
  private final IndentInside myLastLineIndent;

  public LeafBlockWrapper(final Block block,
                          AbstractBlockWrapper parent,
                          WhiteSpace whiteSpaceBefore,
                          FormattingDocumentModel model,
                          LeafBlockWrapper previousTokenBlock,
                          boolean isReadOnly,
                          final TextRange textRange) {
    super(block, whiteSpaceBefore, parent, textRange);
    myPreviousBlock = previousTokenBlock;
    final int lastLineNumber = model.getLineNumber(textRange.getEndOffset());
    myContainsLineFeeds = model.getLineNumber(textRange.getStartOffset()) != lastLineNumber;
    mySymbolsAtTheLastLine = myContainsLineFeeds ? textRange.getEndOffset() - model.getLineStartOffset(lastLineNumber) : textRange.getLength();
    myIsReadOnly = isReadOnly;
    myIsLeaf = block.isLeaf();
    if (myIsLeaf && myContainsLineFeeds) {
      myLastLineIndent = IndentInside.getLastLineIndent(model.getText(textRange).toString());
    } else {
      myLastLineIndent = null;
    }
  }

  public boolean containsLineFeeds() {
    return myContainsLineFeeds;
  }

  public int getSymbolsAtTheLastLine() {
    return mySymbolsAtTheLastLine;
  }

  public LeafBlockWrapper getPreviousBlock() {
    return myPreviousBlock;
  }

  public LeafBlockWrapper getNextBlock() {
    return myNextBlock;
  }

  public void setNextBlock(final LeafBlockWrapper nextBlock) {
    myNextBlock = nextBlock;
  }

  public SpacePropertyImpl getSpaceProperty() {
    return mySpaceProperty;
  }
  public List<Block> getSubBlocks(){
    if (myIsReadOnly) {
      return EMPTY;
    } else {
      return getBlock().getSubBlocks();
    }
  }

  public IndentData calculateOffset(final CodeStyleSettings.IndentOptions options) {
    
    if (myIndentFromParent != null) {
      final AbstractBlockWrapper firstIndentedParent = findFirstIndentedParent();
      final IndentData indentData = new IndentData(myIndentFromParent.getIndentSpaces(), myIndentFromParent.getSpaces());
      if (firstIndentedParent == null) {        
        return indentData;
      } else {
        final WhiteSpace whiteSpace = firstIndentedParent.getWhiteSpace();
        return new IndentData(whiteSpace.getIndentOffset(), whiteSpace.getSpaces()).add(indentData);
      }
    }
    
    if (myParent == null) return new IndentData(0);
    if (getIndent().isAbsolute()) {
      myCanUseFirstChildIndentAsBlockIndent = false;
      AbstractBlockWrapper current = this;
      while (current != null && current.getStartOffset() == getStartOffset()) {
        current.myCanUseFirstChildIndentAsBlockIndent = false;
        current = current.myParent;
      }
    }
    
    return myParent.getChildOffset(this, options, this.getStartOffset());
  }

  public void setSpaceProperty(final SpacePropertyImpl currentSpaceProperty) {
    mySpaceProperty = currentSpaceProperty;
  }

  public IndentInfo calcIndentFromParent() {
    AbstractBlockWrapper firstIndentedParent = findFirstIndentedParent();
    final WhiteSpace mySpace = getWhiteSpace();
    if (firstIndentedParent != null) {
      final WhiteSpace parentSpace = firstIndentedParent.getWhiteSpace();
      return new IndentInfo(0,
                            mySpace.getIndentOffset() - parentSpace.getIndentOffset(),
                            mySpace.getSpaces() - parentSpace.getSpaces());
    } else {
      return null;
    }
  }

  public IndentInfo getIndentFromParent() {
    return myIndentFromParent;
  }

  public boolean isLeaf() {
    return myIsLeaf;
  }

  public IndentInside getLastLineIndent() {
    return myLastLineIndent;
  }
}
