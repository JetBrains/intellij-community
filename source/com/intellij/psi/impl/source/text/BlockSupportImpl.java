package com.intellij.psi.impl.source.text;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.impl.PsiElementFactoryImpl;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.CodeFragmentElement;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.parsing.tabular.ParsingUtil;
import com.intellij.psi.impl.source.parsing.tabular.grammar.Grammar;
import com.intellij.psi.impl.source.parsing.tabular.grammar.GrammarUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.text.BlockSupport;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;

public class BlockSupportImpl extends BlockSupport implements Constants, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.text.BlockSupportImpl");

  public String getComponentName() {
    return "BlockSupport";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public void reparseRange(PsiFile file, int startOffset, int endOffset, String newTextS) throws IncorrectOperationException{
    LOG.assertTrue(file.isValid());
    char[] newText = newTextS.toCharArray();
    int fileLength = file.getTextLength();
    int lengthShift = newText.length - (endOffset - startOffset);

    final PsiFileImpl fileImpl = (PsiFileImpl)file;
    final char[] newFileText = lengthShift > 0 ? new char[fileLength + lengthShift] : new char[fileLength];
    SourceUtil.toBuffer(fileImpl.getTreeElement(), newFileText, 0);

    System.arraycopy(newFileText, endOffset, newFileText, endOffset + lengthShift, fileLength - endOffset);
    System.arraycopy(newText, 0, newFileText, startOffset, newText.length);

    reparseRange(file, startOffset, endOffset, lengthShift, newFileText);
  }


  public void reparseRange(PsiFile file, int startOffset, int endOffset, int lengthShift, char[] newFileText){
    // adjust editor offsets to damage area markers
    if(startOffset > 0) startOffset--;
    reparseRangeInternal(file, startOffset, endOffset, lengthShift, newFileText);
  }

  public void reparseRangeInternal(PsiFile file, int startOffset, int endOffset, int lengthShift, char[] newFileText){
    final PsiFileImpl fileImpl = (PsiFileImpl)file;
    // hack
    final int textLength = file.getTextLength() + lengthShift;

    if(fileImpl.getFileType() == StdFileTypes.JSP){
      makeFullParse(fileImpl.getTreeElement(), newFileText, textLength, fileImpl, fileImpl.getFileType());
      return;
    }
    final FileElement treeFileElement = fileImpl.getTreeElement();

    final LeafElement leafAtStart = treeFileElement.findLeafElementAt(startOffset);
    final LeafElement leafAtEnd = treeFileElement.findLeafElementAt(endOffset);
    TreeElement parent = leafAtStart != null && leafAtEnd != null ? TreeUtil.findCommonParent(leafAtStart, leafAtEnd) : treeFileElement;

    int minErrorLevel = Integer.MAX_VALUE;
    Reparseable bestReparseable = null;
    Reparseable prevReparseable = null;
    boolean theOnlyReparseable = false;
    while(parent != null && !(parent instanceof FileElement)){
      if(parent instanceof Reparseable){
        final TextRange textRange = parent.getTextRange();
        final Reparseable reparseable = (Reparseable)parent;
        boolean lexerChanged = false;
        if(prevReparseable != null){
          lexerChanged = prevReparseable.getLexerClass().equals(reparseable.getLexerClass());
        }

        int currentErrorLevel = reparseable.getErrorsCount(newFileText, textRange.getStartOffset(), textRange.getEndOffset(), lengthShift);
        if(currentErrorLevel == Reparseable.NO_ERRORS){
          final FileElement treeElement = new DummyHolder(parent.getManager(), null, treeFileElement.getCharTable()).getTreeElement();
          final ChameleonElement chameleon = reparseable.createChameleon(newFileText, textRange.getStartOffset(), textRange.getEndOffset() + lengthShift);
          TreeUtil.addChildren(treeElement, chameleon);
          ChangeUtil.replaceAllChildren((CompositeElement)parent, chameleon.transform(treeFileElement.getCharTable(), fileImpl.createLexer()).getTreeParent());
          return;
        }
        else if(currentErrorLevel == Reparseable.FATAL_ERROR){
          prevReparseable = reparseable;
        }
        else if(Math.abs(currentErrorLevel) < Math.abs(minErrorLevel)){
          theOnlyReparseable = bestReparseable == null;
          bestReparseable = reparseable;
          minErrorLevel = currentErrorLevel;
          if (lexerChanged) break;
        }
        // invalid content;
      }
      parent = parent.getTreeParent();
    }

    if(bestReparseable != null && !theOnlyReparseable){
      // best reparseable available
      final CompositeElement treeElement = ((CompositeElement)bestReparseable);
      final TextRange textRange = treeElement.getTextRange();
      final ChameleonElement chameleon = bestReparseable.createChameleon(newFileText, textRange.getStartOffset(), textRange.getEndOffset() + lengthShift);
      chameleon.putUserData(CharTable.CHAR_TABLE_KEY, treeFileElement.getCharTable());
      chameleon.setTreeParent((CompositeElement)parent);
      ChangeUtil.replaceAllChildren(treeElement, chameleon.transform(treeFileElement.getCharTable(), fileImpl.createLexer()).getTreeParent());
    }
    else{
      // file reparse
      FileType fileType = file.getFileType();
      if (file instanceof PsiPlainTextFile){
        fileType = StdFileTypes.PLAIN_TEXT;
      }
      //
      final Grammar grammarByFileType = GrammarUtil.getGrammarByFileType(fileType);
      if(grammarByFileType != null){
        ParsingUtil.reparse(grammarByFileType, treeFileElement.getCharTable(), treeFileElement, newFileText, startOffset, endOffset, lengthShift);
      }
      else{
        makeFullParse(parent, newFileText, textLength, fileImpl, fileType);
      }
    }
  }

  private void makeFullParse(TreeElement parent,
                             char[] newFileText,
                             int textLength,
                             final PsiFileImpl fileImpl,
                             FileType fileType) {
    if(parent instanceof CodeFragmentElement){
      final FileElement holderElement = new DummyHolder(fileImpl.getManager(), null).getTreeElement();
      LeafElement chameleon = Factory.createLeafElement(fileImpl.getContentElementType(), newFileText, 0, textLength, -1, holderElement.getCharTable());
      TreeUtil.addChildren(holderElement, chameleon);
      ChangeUtil.replaceAllChildren((CompositeElement)parent, holderElement);
    }
    else{
      final PsiFileImpl newFile = (PsiFileImpl)PsiElementFactoryImpl.createFileFromText((PsiManagerImpl)fileImpl.getManager(), fileType, fileImpl.getName(), newFileText, 0, textLength);
      final CompositeElement newFileElement = newFile.getTreeElement();
      ChangeUtil.replaceAllChildren(fileImpl.getTreeElement(), newFileElement);
    }
  }
}
