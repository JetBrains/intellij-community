package com.intellij.psi.impl.source.text;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
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
import com.intellij.psi.tree.IChameleonElementType;
import com.intellij.psi.tree.IErrorCounterChameleonElementType;
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
    Project project = fileImpl.getProject();
    final CharTable charTable = fileImpl.getTreeElement().getCharTable();
    // hack
    final int textLength = file.getTextLength() + lengthShift;

    if(fileImpl.getFileType() == StdFileTypes.JSP){
      makeFullParse(fileImpl.getTreeElement(), newFileText, textLength, fileImpl, fileImpl.getFileType());
      return;
    }
    final FileElement treeFileElement = fileImpl.getTreeElement();

    final ASTNode leafAtStart = treeFileElement.findLeafElementAt(startOffset);
    final ASTNode leafAtEnd = treeFileElement.findLeafElementAt(endOffset);
    ASTNode parent = leafAtStart != null && leafAtEnd != null ? TreeUtil.findCommonParent(leafAtStart, leafAtEnd) : treeFileElement;

    int minErrorLevel = Integer.MAX_VALUE;
    ASTNode bestReparseable = null;
    ASTNode prevReparseable = null;
    boolean theOnlyReparseable = false;

    while(parent != null && !(parent instanceof FileElement)){
      if(parent.getElementType() instanceof IChameleonElementType){
        final TextRange textRange = parent.getTextRange();
        final IChameleonElementType reparseable = (IChameleonElementType)parent.getElementType();
        boolean languageChanged = false;
        if(prevReparseable != null){
          languageChanged = prevReparseable.getElementType().getLanguage() != reparseable.getLanguage();
        }

        final String newTextStr = StringFactory.createStringFromConstantArray(newFileText, textRange.getStartOffset(), textRange.getLength() + lengthShift);
        if(reparseable.isParsable(newTextStr, project)){
          final ChameleonElement chameleon =
            (ChameleonElement)Factory.createSingleLeafElement(reparseable, newFileText, textRange.getStartOffset(),
                                                              textRange.getEndOffset() + lengthShift, charTable, file.getManager());
          ChangeUtil.replaceAllChildren((CompositeElement)parent, reparseable.parseContents(chameleon).getTreeParent());
          return;
        }
        else if(reparseable instanceof IErrorCounterChameleonElementType){
          int currentErrorLevel = ((IErrorCounterChameleonElementType)reparseable).getErrorsCount(newTextStr, project);
          if(currentErrorLevel == IErrorCounterChameleonElementType.FATAL_ERROR){
            prevReparseable = parent;
          }
          else if(Math.abs(currentErrorLevel) < Math.abs(minErrorLevel)){
            theOnlyReparseable = bestReparseable == null;
            bestReparseable = parent;
            minErrorLevel = currentErrorLevel;
            if (languageChanged) break;
          }
        }
        // invalid content;
      }
      parent = parent.getTreeParent();
    }

    if(bestReparseable != null && !theOnlyReparseable){
      // best reparseable available
      final ASTNode treeElement = bestReparseable;
      final TextRange textRange = treeElement.getTextRange();
      final ChameleonElement chameleon =
        (ChameleonElement)Factory.createLeafElement(bestReparseable.getElementType(), newFileText, textRange.getStartOffset(),
                                                    textRange.getEndOffset() + lengthShift, -1, treeFileElement.getCharTable());
      chameleon.putUserData(CharTable.CHAR_TABLE_KEY, treeFileElement.getCharTable());
      chameleon.setTreeParent((CompositeElement)parent);
      treeElement.replaceAllChildrenToChildrenOf(chameleon.transform(treeFileElement.getCharTable(), fileImpl.createLexer(), project).getTreeParent());
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

  private void makeFullParse(ASTNode parent,
                             char[] newFileText,
                             int textLength,
                             final PsiFileImpl fileImpl,
                             FileType fileType) {
    if(parent instanceof CodeFragmentElement){
      final FileElement holderElement = new DummyHolder(fileImpl.getManager(), null).getTreeElement();
      TreeUtil.addChildren(holderElement, fileImpl.createContentLeafElement(newFileText, 0, textLength, holderElement.getCharTable()));
      parent.replaceAllChildrenToChildrenOf(holderElement);
    }
    else{
      final PsiFileImpl newFile = (PsiFileImpl)PsiElementFactoryImpl.createFileFromText((PsiManagerImpl)fileImpl.getManager(), fileType, fileImpl.getName(), newFileText, 0, textLength);
      final ASTNode newFileElement = newFile.getTreeElement();
      fileImpl.getTreeElement().replaceAllChildrenToChildrenOf(newFileElement);
    }
  }
}
