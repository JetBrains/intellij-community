/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.formatter;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStatementPart;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyBlock implements Block {
  //TOLATER: fix formatter
  private final PythonLanguage _language;
  private final Alignment _alignment;
  private final Indent _indent;
  private final ASTNode _node;
  private final Wrap _wrap;
  private final CodeStyleSettings _settings;
  private List<Block> _subBlocks = null;
  private final Alignment _childListAlignment;
  private final TokenSet _listElementTypes;
  private static final boolean DUMP_FORMATTING_BLOCKS = false;

  public PyBlock(PythonLanguage language,
                 final ASTNode node,
                 final Alignment alignment,
                 final Indent indent,
                 final Wrap wrap,
                 final CodeStyleSettings settings) {
    _language = language;
    _alignment = alignment;
    _indent = indent;
    _node = node;
    _wrap = wrap;
    _settings = settings;
    _childListAlignment = Alignment.createAlignment();

    _listElementTypes = TokenSet.create(PyElementTypes.LIST_LITERAL_EXPRESSION, PyElementTypes.LIST_COMP_EXPRESSION,
                                        PyElementTypes.DICT_LITERAL_EXPRESSION, PyElementTypes.ARGUMENT_LIST,
                                        PyElementTypes.PARAMETER_LIST);
  }

  @NotNull
  public ASTNode getNode() {
    return _node;
  }

  @NotNull
  public TextRange getTextRange() {
    return _node.getTextRange();
  }

  @NotNull
  public List<Block> getSubBlocks() {
    if (_subBlocks == null) {
      _subBlocks = buildSubBlocks();
      if (DUMP_FORMATTING_BLOCKS) {
        dumpSubBlocks();
      }
    }
    return new ArrayList<Block>(_subBlocks);
  }

  private List<Block> buildSubBlocks() {
    List<Block> blocks = new ArrayList<Block>();
    for (ASTNode child = _node.getFirstChildNode(); child != null; child = child.getTreeNext()) {

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
    IElementType parentType = _node.getElementType();
    IElementType childType = child.getElementType();
    Wrap wrap = null;
    Indent childIndent = Indent.getNoneIndent();
    Alignment childAlignment = null;
    if (childType == PyElementTypes.STATEMENT_LIST || childType == PyElementTypes.IMPORT_ELEMENT) {
      if (hasLineBreakBefore(child)) {
        childIndent = Indent.getNormalIndent();
      }
    }
    if (_listElementTypes.contains(parentType)) {
      wrap = Wrap.createWrap(WrapType.NORMAL, true);
      if (!PyTokenTypes.OPEN_BRACES.contains(childType) && !PyTokenTypes.CLOSE_BRACES.contains(childType)) {
        childAlignment = _childListAlignment;
      }
    }
    if (parentType == PyElementTypes.LIST_LITERAL_EXPRESSION || parentType == PyElementTypes.ARGUMENT_LIST) {
      childIndent = Indent.getContinuationIndent();
    }

    return new PyBlock(_language, child, childAlignment, childIndent, wrap, _settings);
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
    System.out.println("Subblocks of " + _node.getPsi() + ":");
    for (Block block : _subBlocks) {
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
    return _wrap;
  }

  @Nullable
  public Indent getIndent() {
    assert _indent != null;
    return _indent;
  }

  @Nullable
  public Alignment getAlignment() {
    return _alignment;
  }

  @Nullable
  public Spacing getSpacing(Block child1, Block child2) {
    ASTNode childNode1 = ((PyBlock)child1).getNode();
    ASTNode childNode2 = ((PyBlock)child2).getNode();
    IElementType parentType = _node.getElementType();
    IElementType type1 = childNode1.getElementType();
    IElementType type2 = childNode2.getElementType();
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
    //        _settings).getResult();
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
      if (_node.getPsi() instanceof PyFile || _node.getElementType() == PyTokenTypes.COLON) {
        return ChildAttributes.DELEGATE_TO_PREV_CHILD;
      }
      
      PyBlock insertAfterBlock = (PyBlock)_subBlocks.get(newChildIndex - 1);

      System.out.println( // XXX debug
        "getChildAttributes(" + newChildIndex + "): " + ((PyBlock)insertAfterBlock).getNode().getPsi().toString() +
        " " + insertAfterBlock.getTextRange().getStartOffset()
        + ":" + insertAfterBlock.getTextRange().getLength()
      );

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
        if (_node.getElementType() == PyElementTypes.STATEMENT_LIST && lastChild.getPsi() instanceof PsiErrorElement) {
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
      int indent = _settings.getIndentSize(_language.getAssociatedFileType());
      return new ChildAttributes(Indent.getSpaceIndent(indent * statementListsBelow), null);
    }

    /*
    // it might be something like "def foo(): # comment" or "[1, # comment"; jump up to the real thing
    if (_node instanceof PsiComment || _node instanceof PsiWhiteSpace) {
      get
    }
    */


    return new ChildAttributes(getChildIndent(newChildIndex), getChildAlignment());
  }

  private Alignment getChildAlignment() {
    if (_listElementTypes.contains(_node.getElementType())) {
      return _childListAlignment;
    }
    return null;
  }

  private Indent getChildIndent(int newChildIndex) {
    ASTNode lastChild = getLastNonSpaceChild(_node, false);
    if (lastChild != null && lastChild.getElementType() == PyElementTypes.STATEMENT_LIST) {

      PyBlock insertAfterBlock = (PyBlock)_subBlocks.get(newChildIndex - 1);
      ASTNode afterNode = insertAfterBlock.getNode();

      System.out.println( // XXX debug
        "getChildIndent(" + newChildIndex + "): " + ((PyBlock)insertAfterBlock).getNode().getPsi().toString() +
        " " + insertAfterBlock.getTextRange().getStartOffset()
        + ":" + insertAfterBlock.getTextRange().getLength()
      );


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
    if (_listElementTypes.contains(_node.getElementType()) || _node.getPsi() instanceof PyStatementPart) {
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

  private static ASTNode getLastNonSpaceChild(ASTNode node, boolean acceptError) {
    ASTNode lastChild = node.getLastChildNode();
    while (lastChild != null &&
           (lastChild.getElementType() == TokenType.WHITE_SPACE || (!acceptError && lastChild.getPsi() instanceof PsiErrorElement))) {
      lastChild = lastChild.getTreePrev();
    }
    return lastChild;
  }

  public boolean isIncomplete() {
    ASTNode lastChild = getLastNonSpaceChild(_node, false);
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
    return _node.getFirstChildNode() == null;
  }
}
