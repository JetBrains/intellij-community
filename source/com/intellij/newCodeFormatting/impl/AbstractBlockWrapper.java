package com.intellij.newCodeFormatting.impl;

import com.intellij.newCodeFormatting.*;
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
  protected boolean myCanUseFirstChildIndentAsBlockIndent = true;
  
  
  protected IndentInfo myIndentFromParent = null;
  
  

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
    if (getStartOffset() == startOffset) return;
    boolean isFirst = getParent() != null && getStartOffset() == getParent().getStartOffset();
    myTextRange = new TextRange(startOffset, myTextRange.getEndOffset());
    if (isFirst) {
      getParent().arrangeStartOffset(startOffset);
    }
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

  private static IndentData getIndent(CodeStyleSettings.IndentOptions options,
                                      AbstractBlockWrapper block,
                                      final int tokenBlockStartOffset) {
    final IndentImpl indent = block.myParent.getIndent();
    if (indent.getType() == IndentImpl.Type.CONTINUATION) {
      return new IndentData(options.CONTINUATION_INDENT_SIZE);
    }
    if (indent.getType() == IndentImpl.Type.CONTINUATION_WITHOUT_FIRST) {
      if (block.getStartOffset() != block.getParent().getStartOffset() && block.getStartOffset() == tokenBlockStartOffset) {
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

  public IndentData getChildOffset(AbstractBlockWrapper child,
                                   CodeStyleSettings.IndentOptions options,
                                   final int tokenBlockStartOffset) {
    final boolean childOnNewLine = child.getWhiteSpace().containsLineFeeds();
    IndentData childIndent = childOnNewLine || getWhiteSpace().containsLineFeeds() ? getIndent(options, child, tokenBlockStartOffset) : new IndentData(0);

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

    if (getStartOffset() == tokenBlockStartOffset) {
      if (myParent == null) {
        return childIndent;
      }
      else {
        return childIndent.add(myParent.getChildOffset(this, options, tokenBlockStartOffset));
      }
    } else if (!getWhiteSpace().containsLineFeeds()) {
      return childIndent.add(myParent.getChildOffset(this, options, tokenBlockStartOffset));
    } else {
      if (myParent == null) return  childIndent.add(getWhiteSpace());
      if (myParent.getIndent().isAbsolute()) return childIndent.add(myParent.myParent.getChildOffset(myParent, options, tokenBlockStartOffset));
      if (myCanUseFirstChildIndentAsBlockIndent) {
        return childIndent.add(getWhiteSpace());
      }
      else {
        return childIndent.add(myParent.getChildOffset(this, options, tokenBlockStartOffset));
      }
    }
  }

  public void arrangeParentTextRange() {
    if (myParent != null) {
      myParent.arrangeStartOffset(getTextRange().getStartOffset());
    }
  }
  public IndentData calculateChildOffset(final CodeStyleSettings.IndentOptions indentOption, final ChildAttributes childAttributes,
                                         int index) {
    IndentImpl childIndent = (IndentImpl)childAttributes.getChildIndent();

    if (childIndent == null) childIndent = (IndentImpl)Formatter.getInstance().createContinuationWithoutFirstIndent();

    IndentData indent = getIndent(indentOption, index, childIndent);
    if (myParent == null) {
      return indent.add(getWhiteSpace());
    } else if (myCanUseFirstChildIndentAsBlockIndent && getWhiteSpace().containsLineFeeds()) {
      return indent.add(getWhiteSpace());
    }
    else {
      return indent.add(myParent.getChildOffset(this, indentOption, -1));
    }

  }

  private IndentData getIndent(final CodeStyleSettings.IndentOptions options, final int index, IndentImpl indent) {
    if (indent.getType() == IndentImpl.Type.CONTINUATION) {
      return new IndentData(options.CONTINUATION_INDENT_SIZE);
    }
    if (indent.getType() == IndentImpl.Type.CONTINUATION_WITHOUT_FIRST) {
      if (index != 0) {
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

  public void setIndentFromParent(final IndentInfo indentFromParent) {
    myIndentFromParent = indentFromParent;
    if (myIndentFromParent != null) {
      AbstractBlockWrapper parent = myParent;
      if (myParent != null && myParent.getTextRange().getStartOffset() == getTextRange().getStartOffset()) {
        parent.setIndentFromParent(myIndentFromParent);
      }
    }    
  }
  
  protected AbstractBlockWrapper findFirstIndentedParent() {
    if (myParent == null) return null;
    if (getStartOffset() != myParent.getStartOffset() && myParent.getWhiteSpace().containsLineFeeds()) return myParent;
    return myParent.findFirstIndentedParent();
  }
  
}
