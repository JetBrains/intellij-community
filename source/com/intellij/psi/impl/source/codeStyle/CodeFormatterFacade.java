package com.intellij.psi.impl.source.codeStyle;

import com.intellij.codeFormatting.PseudoText;
import com.intellij.codeFormatting.PseudoTextBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.java.JavaCodeFormatter;
import com.intellij.psi.impl.source.codeStyle.javadoc.CommentFormatter;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.CompositeElement;

/**
 *
 */
public class CodeFormatterFacade implements Constants {
  private CodeStyleSettings mySettings;
  private Helper myHelper;
  private IndentAdjusterFacade myIndentAdjuster;
  private CodeWrapperFacade myWrapper;
  private CommentFormatter myCommentFormatter;

  private ASTNode child1;
  private ASTNode child2;
  private BraceEnforcer myBraceEnforcer;
  private CodeFormatter myCodeFormatter = new JavaCodeFormatter();

  public static int USE_NEW_CODE_FORMATTER = -1;

  public CodeFormatterFacade(CodeStyleSettings settings, Helper helper) {
    if (USE_NEW_CODE_FORMATTER < 0) {
      ApplicationEx application = ( (ApplicationEx)ApplicationManager.getApplication());
      boolean internal = application.isInternal();
      boolean unitTestMode = application.isUnitTestMode();
      USE_NEW_CODE_FORMATTER = (internal || unitTestMode) ? 1 : 0;
    }

    mySettings = settings;
    myHelper = helper;
    myIndentAdjuster = new IndentAdjusterFacade(settings, helper);
    myWrapper = new CodeWrapperFacade(settings, helper, myIndentAdjuster);
    myBraceEnforcer = new BraceEnforcer(mySettings);
    myCommentFormatter = new CommentFormatter(helper.getProject());
  }

  private void formatComments(ASTNode element, int startOffset, int endOffset) {
    TextRange range = element.getTextRange();
    if (range.getStartOffset() >= startOffset && range.getEndOffset() <= endOffset) {
      myCommentFormatter.process(element);
    }

    if (element instanceof CompositeElement) {
      for (ASTNode elem = element.getFirstChildNode(); elem != null; elem = elem.getTreeNext()) {
        formatComments(elem, startOffset, endOffset);
      }
    }
  }

  public ASTNode process(ASTNode element, int parent_indent) {
    if (useNewFormatter(myHelper.getFileType())) {
      TextRange range = element.getTextRange();
      int startOffset = range.getStartOffset();
      int endOffset = range.getEndOffset();

      processRange(element, startOffset, endOffset);
      return element;
    }

    if (element instanceof CompositeElement) {
      ASTNode parent = element;
      ChameleonTransforming.transformChildren(parent);

      child1 = null;
      child2 = Helper.shiftForwardToNonSpace(parent.getFirstChildNode());
      int indent = -1;
      for (; child2 != null; child1 = child2, child2 = Helper.shiftForwardToNonSpace(child2.getTreeNext())) {
        if (child1 != null || SourceTreeToPsiMap.treeElementToPsi(parent) instanceof PsiFile) {
          child2 = myCodeFormatter.format(SourceTreeToPsiMap.treeElementToPsi(parent), child1, child2, mySettings, myHelper);
        }

        if (indent < 0) {
          indent = myIndentAdjuster.calculateIndent(child2, -1);
        }

        if (child2 != null) {
          child2 = myIndentAdjuster.adjustIndent(child2, indent);
        }
      }

      element = myWrapper.wrap(element);
      element = myIndentAdjuster.adjustIndent(element, parent_indent);
      myCommentFormatter.process(element);

      indent = -1;
      for (ASTNode child = element.getFirstChildNode(); child != null; child = child.getTreeNext()) {
        if (indent < 0) indent = myIndentAdjuster.calculateIndent(child, -1);
        child = process(child, indent);
      }
    }
    return element;
  }

  public ASTNode processRange(ASTNode element, int startOffset, int endOffset) {
    FileType fileType = myHelper.getFileType();
    if (useNewFormatter(fileType)) {
      PseudoTextBuilder pseudoTextBuilder = fileType.getPseudoTextBuilder();
      if (pseudoTextBuilder == null) {
        return element;
      }
      else {
        PseudoText pseudoText = pseudoTextBuilder.build(myHelper.getProject(), mySettings, SourceTreeToPsiMap.treeElementToPsi(element));
        GeneralCodeFormatter.createSimpleInstance(pseudoText, mySettings, fileType, startOffset, endOffset).format();
        formatComments(element, startOffset, endOffset);
        return element;
      }
    }

    final TextRange range = element.getTextRange();
    final int elementStartOffset = range.getStartOffset();
    return processRange(element, new int[]{startOffset - elementStartOffset, endOffset - elementStartOffset});
  }

  private boolean useNewFormatter(FileType fileType) {
    return (fileType == StdFileTypes.JAVA
            || fileType == StdFileTypes.XML
            || fileType == StdFileTypes.JSPX
            || fileType == StdFileTypes.HTML) &&
           USE_NEW_CODE_FORMATTER == 1;
  }

  private ASTNode processRange(ASTNode element, int[] bounds) {
    if (element instanceof CompositeElement) {
      ASTNode parent = element;
      ChameleonTransforming.transformChildren(parent);

      child1 = null;
      child2 = Helper.shiftForwardToNonSpace(parent.getFirstChildNode());
      int child1Offset = 0;
      int child2Offset;
      for (;
           child2 != null;
           child1 = child2, child2 = Helper.shiftForwardToNonSpace(child2.getTreeNext()), child1Offset = child2Offset) {
        int length1 = child1 != null ? child1.getTextLength() : 0;
        int length2 = child2.getTextLength();
        child2Offset = child1Offset + length1;
        for (ASTNode child = child1 != null ? child1.getTreeNext() : parent.getFirstChildNode();
             child != child2;
             child = child.getTreeNext()) {
          child2Offset += child.getTextLength();
        }
        //Diagnostic.assertTrue(child2Offset == child2.getStartOffsetInParent());
        int offset1 = child1 != null ? child1Offset + length1 : child2Offset;
        int offset2 = child2Offset;
        int offset3 = offset2 + length2;
        if (bounds[0] <= offset1 && offset2 <= bounds[1]) {
          if (child1 != null && child2 != null) {
            child2 = myCodeFormatter.format(SourceTreeToPsiMap.treeElementToPsi(parent), child1, child2, mySettings, myHelper);
            child2Offset = child1Offset + length1;
            for (ASTNode child = child1 != null ? child1.getTreeNext() : parent.getFirstChildNode();
                 child != child2;
                 child = child.getTreeNext()) {
              child2Offset += child.getTextLength();
            }
            //Diagnostic.assertTrue(child2Offset == child2.getStartOffsetInParent());
            bounds[1] += child2Offset - offset2;
            offset2 = child2Offset;
            offset3 = offset2 + length2;
          }
        }
        if (bounds[0] <= offset2 && offset3 <= bounds[1]) {
          if (bounds[0] > offset1) {
            String space = Helper.getSpaceText(parent, child1, child2);
            int index = bounds[0] - offset1 - 1;
            if (space.indexOf('\n', index) < 0 && space.indexOf('\r', index) < 0) continue;
          }
          child2 = myIndentAdjuster.adjustIndent(child2);
          child2 = myWrapper.wrap(child2);
          child2 =
          SourceTreeToPsiMap.psiElementToTree(myBraceEnforcer.process(SourceTreeToPsiMap.treeElementToPsi(child2)));
          child2 = myCommentFormatter.formatComment(child2);
          child2Offset = child1Offset + length1;
          for (ASTNode child = child1 != null ? child1.getTreeNext() : parent.getFirstChildNode();
               child != child2;
               child = child.getTreeNext()) {
            child2Offset += child.getTextLength();
          }
          //Diagnostic.assertTrue(child2Offset == child2.getStartOffsetInParent());
          bounds[1] += child2Offset + child2.getTextLength() - offset3;
        }
      }

      int offset = 0;
      for (ASTNode child = element.getFirstChildNode(); child != null; child = child.getTreeNext()) {
        //Diagnostic.assertTrue(offset == child.getStartOffsetInParent());
        int offset1 = offset;
        int offset2 = offset + child.getTextLength();
        if (bounds[0] <= offset2 && offset1 <= bounds[1]) {
          bounds[0] -= offset1;
          bounds[1] -= offset1;
          child = processRange(child, bounds);
          bounds[0] += offset1;
          bounds[1] += offset1;
          offset2 = offset + child.getTextLength();
        }
        offset = offset2;
      }
    }
    return element;
  }

  public ASTNode processSpace(ASTNode child1, ASTNode child2) {
    this.child1 = child1;
    this.child2 = child2;
    ASTNode parent = child1 != null ? child1.getTreeParent() : ((child2 != null) ? child2.getTreeParent() : null);
    if ((child1 == null || child2 == null) && !(SourceTreeToPsiMap.treeElementToPsi(parent) instanceof PsiFile)) {
      if (parent != null) myHelper.makeSpace(parent, child1, child2, "");
      return child2;
    }
    child2 = myCodeFormatter.format(SourceTreeToPsiMap.treeElementToPsi(parent), child1, child2, mySettings, myHelper);
    if (child1 != null && child1.getElementType() == END_OF_LINE_COMMENT) {
      if (myHelper.getLineBreakCount(parent, child1, child2) == 0) {
        myHelper.makeVerticalSpace(parent, child1, child2, 0);
      }
    }
    return child2;
  }
}

