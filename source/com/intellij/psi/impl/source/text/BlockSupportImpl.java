package com.intellij.psi.impl.source.text;

import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.jsp.JspFileViewProvider;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.PsiElementFactoryImpl;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.source.CodeFragmentElement;
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
import com.intellij.util.text.CharArrayCharSequence;

import java.util.Set;

public class BlockSupportImpl extends BlockSupport implements ProjectComponent {
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
    final PsiFileImpl psiFile = (PsiFileImpl)file;
    final Document document = psiFile.getViewProvider().getDocument();
    document.replaceString(startOffset, endOffset, newTextS);
    PsiDocumentManager.getInstance(psiFile.getProject()).commitDocument(document);

    //final CompositeElement element = psiFile.calcTreeElement();
    //char[] newText = newTextS.toCharArray();
    //int fileLength = element.getTextLength();
    //int lengthShift = newText.length - (endOffset - startOffset);
    //
    //final PsiFileImpl fileImpl = (PsiFileImpl)file;
    //final char[] newFileText = lengthShift > 0 ? new char[fileLength + lengthShift] : new char[fileLength];
    //SourceUtil.toBuffer(fileImpl.getTreeElement(), newFileText, 0);
    //
    //System.arraycopy(newFileText, endOffset, newFileText, endOffset + lengthShift, fileLength - endOffset);
    //System.arraycopy(newText, 0, newFileText, startOffset, newText.length);
    //
    //if(startOffset > 0) startOffset--;
    //reparseRangeInternal(file, startOffset, endOffset, lengthShift, newFileText);
  }


  public void reparseRange(PsiFile file, int startOffset, int endOffset, int lengthShift, char[] newFileText){
    // adjust editor offsets to damage area markers
    if(startOffset > 0) startOffset--;
    reparseRangeInternal(file, startOffset, endOffset, lengthShift, newFileText);
  }

  private static void reparseRangeInternal(PsiFile file, int startOffset, int endOffset, int lengthShift, char[] newFileText){
    Set<String> oldTaglibPrefixes = null;
    if(file.getLanguage() == StdLanguages.JSP){
      oldTaglibPrefixes = ((JspFileViewProvider)file.getViewProvider()).getKnownTaglibPrefixes();
    }
    try{
      file.getViewProvider().beforeContentsSynchronized();
      final PsiFileImpl fileImpl = (PsiFileImpl)file;
      Project project = fileImpl.getProject();
      final CharTable charTable = fileImpl.getTreeElement().getCharTable();
      // hack
      final int textLength = file.getTextLength() + lengthShift;

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
                                                                textRange.getEndOffset() + lengthShift, charTable, file.getManager(), fileImpl);
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
        //boolean leafChangeOptimized = false;
        //Document document = PsiDocumentManager.getInstance(project).getDocument(fileImpl);
        //if (document != null) {
        //  int changedOffset;
        //  synchronized (document) {
        //    Integer offset = document.getUserData(LexerEditorHighlighter.CHANGED_TOKEN_START_OFFSET);
        //    changedOffset = offset == null ? -1 : offset.intValue();
        //    document.putUserData(LexerEditorHighlighter.CHANGED_TOKEN_START_OFFSET, null);
        //  }
        //  leafChangeOptimized = changedOffset != -1 && optimizeLeafChange(treeFileElement, newFileText, startOffset, endOffset, lengthShift, changedOffset);
        //}
        //if (leafChangeOptimized) {
        //  return;
        //}

        // file reparse
        FileType fileType = file.getFileType();
        if (file instanceof PsiPlainTextFile){
          fileType = StdFileTypes.PLAIN_TEXT;
        }

        final Grammar grammarByFileType = GrammarUtil.getGrammarByFileType(fileType);
        if(grammarByFileType != null){
          Set<String> newTaglibPrefixes = null;
          if(file.getLanguage() == StdLanguages.JSP){
            newTaglibPrefixes = ((JspFileViewProvider)file.getViewProvider()).getKnownTaglibPrefixes();
          }
          if(newTaglibPrefixes == null || newTaglibPrefixes.equals(oldTaglibPrefixes))
            ParsingUtil.reparse(grammarByFileType, treeFileElement.getCharTable(), treeFileElement, newFileText, startOffset, endOffset, lengthShift,
                                file.getViewProvider());
          else makeFullParse(parent, newFileText, textLength, fileImpl, fileType);
        }
        else{
          makeFullParse(parent, newFileText, textLength, fileImpl, fileType);
        }
      }
    }
    finally{
      file.getViewProvider().contentsSynchronized();
    }
  }

  private static boolean hasErrorElementChild(ASTNode element) {
    if (element == null) return false;
    if (element instanceof PsiErrorElement) return true;
    for (ASTNode child = element.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (child instanceof PsiErrorElement) return true;
    }
    return false;
  }

  private static boolean optimizeLeafChange(final FileElement treeFileElement,
                                            final char[] newFileText,
                                            int startOffset,
                                            final int endOffset, final int lengthDiff, final int changedOffset) {
    final LeafElement leafElement = treeFileElement.findLeafElementAt(startOffset);
    if (leafElement == null
        || hasErrorElementChild(leafElement.getTreeParent())
        || hasErrorElementChild(leafElement.getTreeNext())
        || hasErrorElementChild(leafElement.getTreePrev())) return false;
    if (!leafElement.getTextRange().contains(new TextRange(startOffset, endOffset))) return false;
    final LeafElement leafElementToChange = treeFileElement.findLeafElementAt(changedOffset);
    if (leafElementToChange == null) return false;
    TextRange leafRangeToChange = leafElementToChange.getTextRange();
    LeafElement newElement = Factory.createLeafElement(leafElementToChange.getElementType(), newFileText, leafRangeToChange.getStartOffset(), leafRangeToChange.getEndOffset() + lengthDiff, -1, treeFileElement.getCharTable());
    newElement.putUserData(CharTable.CHAR_TABLE_KEY, treeFileElement.getCharTable());
    ChangeUtil.replaceChild(leafElementToChange.getTreeParent(), leafElementToChange, newElement);
    return true;
  }

  private static void makeFullParse(ASTNode parent,
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
      final PsiManagerImpl manager = (PsiManagerImpl)fileImpl.getManager();
      final PsiElementFactoryImpl factory = (PsiElementFactoryImpl)manager.getElementFactory();
      final CharArrayCharSequence seq = new CharArrayCharSequence(newFileText, 0, textLength);
      final PsiFileImpl newFile = (PsiFileImpl)factory.createFileFromText(fileImpl.getName(), fileType, seq, fileImpl.getModificationStamp(), true, false);
      newFile.setOriginalFile(fileImpl);
      final ASTNode newFileElement = newFile.getNode();
      final RepositoryManager repositoryManager = manager.getRepositoryManager();
      final FileElement fileElement = (FileElement)fileImpl.getNode();
      final int oldLength = fileElement.getTextLength();
      sendPsiBeforeEvent(fileImpl);
      if(repositoryManager != null) repositoryManager.beforeChildAddedOrRemoved(fileImpl, fileElement);
      if(fileElement.getFirstChildNode() != null)
        TreeUtil.removeRange(fileElement.getFirstChildNode(), null);
      final ASTNode firstChildNode = newFileElement.getFirstChildNode();
      if (firstChildNode != null)
        TreeUtil.addChildren(fileElement, (TreeElement)firstChildNode);
      fileImpl.getTreeElement().setCharTable(newFile.getTreeElement().getCharTable());
      if(repositoryManager != null) repositoryManager.beforeChildAddedOrRemoved(fileImpl, fileElement);
      manager.invalidateFile(fileImpl);
      fileElement.subtreeChanged();
      sendPsiAfterEvent(fileImpl, oldLength);
    }
  }

  private static void sendPsiAfterEvent(final PsiFileImpl scope, int oldLength) {
    if(!scope.isPhysical()) return;
    final PsiManagerImpl manager = (PsiManagerImpl)scope.getManager();
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(manager);
    event.setParent(scope);
    event.setFile(scope);
    event.setOffset(0);
    event.setOldLength(oldLength);
    manager.childrenChanged(event);
  }

  private static void sendPsiBeforeEvent(final PsiFile scope) {
    if(!scope.isPhysical()) return;
    final PsiManagerImpl manager = (PsiManagerImpl)scope.getManager();
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(manager);
    event.setParent(scope);
    event.setFile(scope);
    event.setOffset(0);
    event.setOldLength(scope.getTextLength());
    manager.beforeChildrenChange(event);
  }
}
