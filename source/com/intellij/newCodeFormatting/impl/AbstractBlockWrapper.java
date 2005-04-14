package com.intellij.newCodeFormatting.impl;

import com.intellij.newCodeFormatting.Block;
import com.intellij.newCodeFormatting.Indent;
import com.intellij.newCodeFormatting.Formatter;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Apr 14, 2005
 * Time: 9:40:01 PM
 * To change this template use File | Settings | File Templates.
 */
public class AbstractBlockWrapper {
  protected final Block myBlock;
  protected final WhiteSpace myWhiteSpace;
  protected final AbstractBlockWrapper myParent;
  protected TextRange myTextRange;
  private boolean myCanUseFirstChildIndentAsBlockIndent = true;

  public AbstractBlockWrapper(final Block block, final WhiteSpace whiteSpace, final AbstractBlockWrapper parent, final TextRange textRange) {
    myBlock = block;
    myWhiteSpace = whiteSpace;
    myParent = parent;
    myTextRange = textRange;
  }

  public WhiteSpace getWhiteSpace() {
    return myWhiteSpace;
  }

  public Block getBlock() {
    return myBlock;
  }

  public WrapImpl[] getWraps() {
    final ArrayList<WrapImpl> result = new ArrayList<WrapImpl>();
    AbstractBlockWrapper current = this;
    while(current != null && current.getStartOffset() == getStartOffset()) {
      final WrapImpl wrap = (WrapImpl)current.getBlock().getWrap();
      if (wrap != null && !result.contains(wrap)) result.add(0, wrap);
      current = current.myParent;
    }
    return result.toArray(new WrapImpl[result.size()]);
  }

  public TextRange getTextRange() {
    return myTextRange;
  }

  protected void arrangeStartOffset(final int startOffset) {
    myTextRange = new TextRange(startOffset, myTextRange.getEndOffset());
  }

  public int getStartOffset() {
    return getTextRange().getStartOffset();
  }

  public IndentImpl getIndent(){
    final Indent indent = getBlock().getIndent();
    if (indent == null) {
      return (IndentImpl)Formatter.getInstance().createContinuationWithoutFirstIndent();
    } else {
      return (IndentImpl)indent;
    }
  }

  public AbstractBlockWrapper getParent() {
    return myParent;
  }

  public WrapImpl getWrap() {
    final WrapImpl[] wraps = getWraps();
    if (wraps.length == 0) return null;
    return wraps[0];
  }

  public void reset() {
    myCanUseFirstChildIndentAsBlockIndent = true;
    final AlignmentImpl alignment = ((AlignmentImpl)getBlock().getAlignment());
    if (alignment != null) alignment.reset();
    final WrapImpl wrap = ((WrapImpl)getBlock().getWrap());
    if (wrap != null) wrap.reset();

  }

  private static IndentData getIndent(CodeStyleSettings.IndentOptions options, AbstractBlockWrapper block, final AbstractBlockWrapper tokenBlock) {
    final IndentImpl indent = block.myParent.getIndent();
    if (indent.getType() == IndentImpl.Type.CONTINUATION) {
      return new IndentData(options.CONTINUATION_INDENT_SIZE);
    }
    if (indent.getType() == IndentImpl.Type.CONTINUATION_WITHOUT_FIRST) {
      if (block.getStartOffset() != block.getParent().getStartOffset() && block.getStartOffset() == tokenBlock.getStartOffset()) {
        return new IndentData(options.CONTINUATION_INDENT_SIZE);
      }
      else {
        return new IndentData(0);
      }
    }
    if (indent.getType() == IndentImpl.Type.LABEL) return new IndentData(options.LABEL_INDENT_SIZE);
    if (indent.getType() == IndentImpl.Type.NONE) return new IndentData(0);
    if (indent.getType() == IndentImpl.Type.SPACES) return new IndentData(0, indent.getSpaces());
    return new IndentData(options.INDENT_SIZE);

  }

  public IndentData getChildOffset(AbstractBlockWrapper child, AbstractBlockWrapper tokenBlock, CodeStyleSettings.IndentOptions options) {
    final boolean childOnNewLine = child.getWhiteSpace().containsLineFeeds();
    IndentData childIndent = childOnNewLine || getWhiteSpace().containsLineFeeds() ? getIndent(options, child, tokenBlock) : new IndentData(0);

    if (childOnNewLine && getIndent().isAbsolute()) {
      myCanUseFirstChildIndentAsBlockIndent = false;
      AbstractBlockWrapper current = this;
      while (current != null && current.getStartOffset() == getStartOffset()) {
        current.myCanUseFirstChildIndentAsBlockIndent = false;
        current = current.myParent;
      }
      return childIndent;
    }

    if (child.getStartOffset() == getStartOffset()) {
      myCanUseFirstChildIndentAsBlockIndent = myCanUseFirstChildIndentAsBlockIndent &&
                                              child.myCanUseFirstChildIndentAsBlockIndent &&
                                              childIndent.isEmpty();
    }

    if (getStartOffset() == tokenBlock.getStartOffset()) {
      if (myParent == null) {
        return childIndent;
      }
      else {
        return childIndent.add(myParent.getChildOffset(this, tokenBlock, options));
      }
    } else if (!getWhiteSpace().containsLineFeeds()) {
      return childIndent.add(myParent.getChildOffset(this, tokenBlock,options));
    } else {
      if (myParent == null) return  childIndent.add(getWhiteSpace());
      if (myParent.getIndent().isAbsolute()) return childIndent.add(myParent.myParent.getChildOffset(myParent, tokenBlock, options));
      if (myCanUseFirstChildIndentAsBlockIndent) {
        return childIndent.add(getWhiteSpace());
      }
      else {
        return childIndent.add(myParent.getChildOffset(this, tokenBlock, options));
      }
    }
  }

  public void arrangeParentTextRange() {
    if (myParent != null) {
      myParent.arrangeStartOffset(getTextRange().getStartOffset());
    }
  }


}
