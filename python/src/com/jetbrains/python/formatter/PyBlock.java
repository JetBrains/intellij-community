package com.jetbrains.python.formatter;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.psi.PyUtil.sure;

/**
 * @author yole
 */
public class PyBlock implements Block {
  private final PythonLanguage myLanguage;
  private final Alignment myAlignment;
  private final Indent myIndent;
  private final ASTNode myNode;
  private final Wrap myWrap;
  private final CodeStyleSettings mySettings;
  private List<Block> mySubBlocks = null;
  private final Alignment myChildListAlignment;
  private final TokenSet myListElementTypes;
  private static final boolean DUMP_FORMATTING_BLOCKS = false;

  public PyBlock(PythonLanguage language,
                 final ASTNode node,
                 final Alignment alignment,
                 final Indent indent,
                 final Wrap wrap,
                 final CodeStyleSettings settings) {
    myLanguage = language;
    myAlignment = alignment;
    myIndent = indent;
    myNode = node;
    myWrap = wrap;
    mySettings = settings;
    myChildListAlignment = Alignment.createAlignment();

    myListElementTypes = TokenSet.create(
      PyElementTypes.LIST_LITERAL_EXPRESSION,
      PyElementTypes.LIST_COMP_EXPRESSION,
      PyElementTypes.DICT_LITERAL_EXPRESSION,
      PyElementTypes.ARGUMENT_LIST,
      PyElementTypes.PARAMETER_LIST
    );
  }

  @NotNull
  public ASTNode getNode() {
    return myNode;
  }

  @NotNull
  public TextRange getTextRange() {
    return myNode.getTextRange();
  }

  @NotNull
  public List<Block> getSubBlocks() {
    if (mySubBlocks == null) {
      mySubBlocks = buildSubBlocks();
      if (DUMP_FORMATTING_BLOCKS) {
        dumpSubBlocks();
      }
    }
    return new ArrayList<Block>(mySubBlocks);
  }

  private List<Block> buildSubBlocks() {
    List<Block> blocks = new ArrayList<Block>();
    for (ASTNode child = myNode.getFirstChildNode(); child != null; child = child.getTreeNext()) {

      IElementType childType = child.getElementType();

      if (child.getTextRange().getLength() == 0) continue;

      if (childType == TokenType.WHITE_SPACE) {
        // don't break this block into sub-blocks if some of the
        // whitespace between sub-blocks would include \ characters,
        // because the IDEA 5.0/5.1 formatter core requires that whitespace
        // between blocks must include whitespace-only characters
        if (child.getText().indexOf('\\') >= 0) {
          return Collections.emptyList();
        }

        continue;
      }

      blocks.add(buildSubBlock(child));
    }
    return Collections.unmodifiableList(blocks);
  }

  private PyBlock buildSubBlock(ASTNode child) {
    IElementType parentType = myNode.getElementType();
    IElementType childType = child.getElementType();
    Wrap wrap = null;
    Indent childIndent = Indent.getNoneIndent();
    Alignment childAlignment = null;
    if (childType == PyElementTypes.STATEMENT_LIST || childType == PyElementTypes.IMPORT_ELEMENT) {
      if (hasLineBreakBefore(child)) {
        childIndent = Indent.getNormalIndent();
      }
    }
    if (myListElementTypes.contains(parentType)) {
      wrap = Wrap.createWrap(WrapType.NORMAL, true);
      if (!PyTokenTypes.OPEN_BRACES.contains(childType) && !PyTokenTypes.CLOSE_BRACES.contains(childType)) {
        childAlignment = myChildListAlignment;
      }
    }
    if (parentType == PyElementTypes.LIST_LITERAL_EXPRESSION || parentType == PyElementTypes.ARGUMENT_LIST) {
      childIndent = Indent.getContinuationIndent();
    }
    try { // maybe enter was pressed and cut us from a previous (nested) statement list
      PsiElement prev = sure(child.getPsi().getPrevSibling());
      sure(prev instanceof PyStatement);
      PsiElement last_child = PsiTreeUtil.getDeepestLast(prev);
      sure(last_child.getParent() instanceof PyStatementList);
      childIndent = Indent.getNormalIndent();
    }
    catch (IncorrectOperationException ignored) {
      // not our cup of tea
    }

    return new PyBlock(myLanguage, child, childAlignment, childIndent, wrap, mySettings);
  }

  private static boolean hasLineBreakBefore(ASTNode child) {
    ASTNode prevNode = child.getTreePrev();
    if (prevNode != null && prevNode.getElementType() == TokenType.WHITE_SPACE) {
      String prevNodeText = prevNode.getText();
      if (prevNodeText.indexOf('\n') >= 0) {
        return true;
      }
    }
    return false;
  }

  private void dumpSubBlocks() {
    System.out.println("Subblocks of " + myNode.getPsi() + ":");
    for (Block block : mySubBlocks) {
      if (block instanceof PyBlock) {
        System.out.println("  " + ((PyBlock)block).getNode().getPsi().toString() + " " + block.getTextRange().getStartOffset() + ":" + block
            .getTextRange().getLength());
      }
      else {
        System.out.println("  <unknown block>");
      }
    }
  }

  @Nullable
  public Wrap getWrap() {
    return myWrap;
  }

  @Nullable
  public Indent getIndent() {
    assert myIndent != null;
    return myIndent;
  }

  @Nullable
  public Alignment getAlignment() {
    return myAlignment;
  }

  @Nullable
  public Spacing getSpacing(Block child1, Block child2) {
    ASTNode childNode1 = ((PyBlock)child1).getNode();
    ASTNode childNode2 = ((PyBlock)child2).getNode();
    IElementType parentType = myNode.getElementType();
    IElementType type1 = childNode1.getElementType();
    IElementType type2 = childNode2.getElementType();

    if (type1 == PyElementTypes.FUNCTION_DECLARATION) {
      PyStatementList func_statement_list = ((PyFunction)(childNode1.getPsi())).getStatementList();
      if (childNode2.getTreeParent().getPsi() == func_statement_list) {
        // statements inside the list
        int blankLines = mySettings.BLANK_LINES_AROUND_METHOD + 1;
        return Spacing.createSpacing(0, 0, blankLines, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      }
      else if (func_statement_list.getStatements().length == 0) {
        // misaligned statements intended for the list
        Document doc = func_statement_list.getContainingFile().getViewProvider().getDocument();
        if (doc != null) {
          final int declaration_offset = childNode1.getStartOffset();
          int line = doc.getLineNumber(declaration_offset);
          int declaration_indent = declaration_offset - doc.getLineStartOffset(line);
          final int indent_size = mySettings.getIndentSize(PythonFileType.INSTANCE) + declaration_indent;
          return Spacing.createSpacing(indent_size, indent_size, 0, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
        }
      }
    }

    if (isStatementOrDeclaration(type1) && isStatementOrDeclaration(type2)) {
      return Spacing.createSpacing(0, Integer.MAX_VALUE, 1, false, 1);
    }
/*
        if (type1 == PyTokenTypes.COLON && type2 == PyElementTypes.STATEMENT_LIST) {
            return Spacing.createSpacing(0, Integer.MAX_VALUE, 1, true, Integer.MAX_VALUE);
        }
*/

    //if (parentType == PyElementTypes.ARGUMENT_LIST
    //    || parentType == PyElementTypes.LIST_LITERAL_EXPRESSION) {
    //  if (type1 == PyTokenTypes.COMMA && PyElementTypes.EXPRESSIONS.contains(type2)) {
    //    return Spacing.createSpacing(1, 1, 0, true, Integer.MAX_VALUE);
    //  }
    //}
    //if (PyElementTypes.STATEMENTS.contains(type1)
    //    && PyElementTypes.STATEMENTS.contains(type2)) {
    //  return Spacing.createSpacing(1, Integer.MAX_VALUE, 1, true, Integer.MAX_VALUE);
    //}

    //return new PySpacingProcessor(getNode(), childNode1, childNode2,
    //        mySettings).getResult();
    //return Spacing.createSpacing(0, Integer.MAX_VALUE, 1, true, Integer.MAX_VALUE);

    return null;
  }

  private static boolean isStatementOrDeclaration(final IElementType type) {
    return PyElementTypes.STATEMENTS.contains(type) ||
           type == PyElementTypes.CLASS_DECLARATION || 
           type == PyElementTypes.FUNCTION_DECLARATION;
  }

  @NotNull
  public ChildAttributes getChildAttributes(int newChildIndex) {
    int statementListsBelow = 0;
    if (newChildIndex > 0) {
      // always pass decision to a sane block from top level from file or definition
      if (myNode.getPsi() instanceof PyFile || myNode.getElementType() == PyTokenTypes.COLON) {
        return ChildAttributes.DELEGATE_TO_PREV_CHILD;
      }
      
      PyBlock insertAfterBlock = (PyBlock)mySubBlocks.get(newChildIndex - 1);

      ASTNode prevNode = insertAfterBlock.getNode();
      PsiElement prevElt = prevNode.getPsi();

      // stmt lists, parts and definitions should also think for themselves
      if (prevElt instanceof PyStatementList || prevElt instanceof PyStatementPart) {
        return ChildAttributes.DELEGATE_TO_PREV_CHILD;
      }

      ASTNode lastChild = insertAfterBlock.getNode();

      // HACK? This code fragment is needed to make testClass2() pass,
      // but I don't quite understand why it is necessary and why the formatter
      // doesn't request childAttributes from the correct block
      while (lastChild != null) {
        IElementType last_type = lastChild.getElementType();
        if ( last_type == PyElementTypes.STATEMENT_LIST && hasLineBreakBefore(lastChild)) {
          statementListsBelow++;
        }
        else if (statementListsBelow > 0 && lastChild.getPsi() instanceof PsiErrorElement) {
          statementListsBelow++;
        }
        if (myNode.getElementType() == PyElementTypes.STATEMENT_LIST && lastChild.getPsi() instanceof PsiErrorElement) {
          return ChildAttributes.DELEGATE_TO_PREV_CHILD;
        }
        lastChild = getLastNonSpaceChild(lastChild, true);
      }
    }

    // HACKETY-HACK
    // If a multi-step dedent follows the cursor position (see testMultiDedent()),
    // the whitespace (which must be a single Py:LINE_BREAK token) gets attached
    // to the outermost indented block (because we may not consume the DEDENT
    // tokens while parsing inner blocks). The policy is to put the indent to
    // the innermost block, so we need to resolve the situation here. Nested
    // delegation sometimes causes NPEs in formatter core, so we calculate the
    // correct indent manually.
    if (statementListsBelow > 0) { // was 1... strange
      int indent = mySettings.getIndentSize(myLanguage.getAssociatedFileType());
      return new ChildAttributes(Indent.getSpaceIndent(indent * statementListsBelow), null);
    }

    /*
    // it might be something like "def foo(): # comment" or "[1, # comment"; jump up to the real thing
    if (myNode instanceof PsiComment || myNode instanceof PsiWhiteSpace) {
      get
    }
    */


    return new ChildAttributes(getChildIndent(newChildIndex), getChildAlignment());
  }

  private Alignment getChildAlignment() {
    if (myListElementTypes.contains(myNode.getElementType())) {
      return myChildListAlignment;
    }
    return null;
  }

  private Indent getChildIndent(int newChildIndex) {
    ASTNode lastChild = getLastNonSpaceChild(myNode, false);
    if (lastChild != null && lastChild.getElementType() == PyElementTypes.STATEMENT_LIST && mySubBlocks.size() >= newChildIndex) {
      PyBlock insertAfterBlock = (PyBlock)mySubBlocks.get(newChildIndex - 1);
      ASTNode afterNode = insertAfterBlock.getNode();


      // handle pressing Enter after colon and before first statement in
      // existing statement list
      if (afterNode.getElementType() == PyElementTypes.STATEMENT_LIST || afterNode.getElementType() == PyTokenTypes.COLON) {
        return Indent.getNormalIndent();
      }

      // handle pressing Enter after colon when there is nothing in the
      // statement list
      ASTNode lastFirstChild = lastChild.getFirstChildNode();
      if (lastFirstChild != null && lastFirstChild == lastChild.getLastChildNode() && lastFirstChild.getPsi() instanceof PsiErrorElement) {
        return Indent.getNormalIndent();
      }
    }
    else if (lastChild != null && PyElementTypes.LIST_LIKE_EXPRESSIONS.contains(lastChild.getElementType())) {
      // handle pressing enter at the end of a list literal when there's no closing paren or bracket 
      ASTNode lastLastChild = lastChild.getLastChildNode();
      if (lastLastChild != null && lastLastChild.getPsi() instanceof PsiErrorElement) {
        // we're at a place like this: [foo, ... bar, <caret>
        // we'd rather align to foo. this may be not a multiple of tabs.
        PsiElement expr = lastChild.getPsi();
        PsiElement exprItem = expr.getFirstChild();
        boolean found = false;
        while (exprItem != null) { // find a worthy element to align to
          if (exprItem instanceof PyElement) {
            found = true; // align to foo in "[foo,"
            break;
          }
          if (exprItem instanceof PsiComment) {
            found = true; // align to foo in "[ # foo,"
            break;
          }
          exprItem = exprItem.getNextSibling();
        }
        if (found) {
          PsiDocumentManager docMgr = PsiDocumentManager.getInstance(exprItem.getProject());
          Document doc = docMgr.getDocument(exprItem.getContainingFile());
          if (doc != null) {
            int line_num = doc.getLineNumber(exprItem.getTextOffset());
            int item_col = exprItem.getTextOffset() - doc.getLineStartOffset(line_num);
            PsiElement here_elt = getNode().getPsi();
            line_num = doc.getLineNumber(here_elt.getTextOffset());
            int node_col = here_elt.getTextOffset() - doc.getLineStartOffset(line_num);
            int padding = item_col - node_col;
            if (padding > 0) { // negative is a syntax error,  but possible
              return Indent.getSpaceIndent(padding);
            }
          }
        }
        return Indent.getContinuationIndent(); // a fallback
      }
    }

    // constructs that imply indent for their children
    if (myListElementTypes.contains(myNode.getElementType()) || myNode.getPsi() instanceof PyStatementPart) {
      return Indent.getNormalIndent();
    }

    return Indent.getNoneIndent();
    //return null;

/*
        Indent indent;
        if (isIncomplete()) {
            indent = Indent.getContinuationIndent();
        } else {
            indent = Indent.getNoneIndent();
        }
        return indent;
*/
  }

  @Nullable
  private static ASTNode getLastNonSpaceChild(ASTNode node, boolean acceptError) {
    ASTNode lastChild = node.getLastChildNode();
    while (lastChild != null &&
           (lastChild.getElementType() == TokenType.WHITE_SPACE || (!acceptError && lastChild.getPsi() instanceof PsiErrorElement))) {
      lastChild = lastChild.getTreePrev();
    }
    return lastChild;
  }

  public boolean isIncomplete() {
    ASTNode lastChild = getLastNonSpaceChild(myNode, false);
    if (lastChild != null && lastChild.getElementType() == PyElementTypes.STATEMENT_LIST) {
      // only multiline statement lists are considered incomplete
      ASTNode statementListPrev = lastChild.getTreePrev();
      if (statementListPrev != null && statementListPrev.getText().indexOf('\n') >= 0) {
        return true;
      }
    }

    return false;
  }

  public boolean isLeaf() {
    return myNode.getFirstChildNode() == null;
  }
}
