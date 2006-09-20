package com.intellij.psi.impl.source.text;

import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.TreeAspectEvent;
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.pom.tree.events.impl.ChangeInfoImpl;
import com.intellij.pom.tree.events.impl.ReplaceChangeInfoImpl;
import com.intellij.pom.tree.events.impl.TreeChangeEventImpl;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiElementFactoryImpl;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.source.CodeFragmentElement;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.parsing.tabular.ParsingUtil;
import com.intellij.psi.impl.source.parsing.tabular.grammar.Grammar;
import com.intellij.psi.impl.source.parsing.tabular.grammar.GrammarUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.text.BlockSupport;
import com.intellij.psi.tree.IChameleonElementType;
import com.intellij.psi.tree.IErrorCounterChameleonElementType;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.diff.DiffTree;
import com.intellij.util.diff.DiffTreeChangeBuilder;
import com.intellij.util.text.CharArrayCharSequence;

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

  public void reparseRange(PsiFile file, int startOffset, int endOffset, String newTextS) throws IncorrectOperationException {
    LOG.assertTrue(file.isValid());
    final PsiFileImpl psiFile = (PsiFileImpl)file;
    final Document document = psiFile.getViewProvider().getDocument();
    assert document != null;
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


  public void reparseRange(final PsiFile file,
                           final int startOffset,
                           final int endOffset,
                           final int lengthShift,
                           final char[] newFileText) {
    // adjust editor offsets to damage area markers
    file.getManager().performActionWithFormatterDisabled(new Runnable() {
      public void run() {
        reparseRangeInternal(file, startOffset > 0 ? startOffset - 1 : 0, endOffset, lengthShift, newFileText);
      }
    });
  }

  private static void reparseRangeInternal(PsiFile file, int startOffset, int endOffset, int lengthShift, char[] newFileText) {
    try {
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

      while (parent != null && !(parent instanceof FileElement)) {
        if (parent.getElementType()instanceof IChameleonElementType) {
          final TextRange textRange = parent.getTextRange();
          final IChameleonElementType reparseable = (IChameleonElementType)parent.getElementType();
          boolean languageChanged = false;
          if (prevReparseable != null) {
            languageChanged = prevReparseable.getElementType().getLanguage() != reparseable.getLanguage();
          }

          final String newTextStr =
            StringFactory.createStringFromConstantArray(newFileText, textRange.getStartOffset(), textRange.getLength() + lengthShift);
          if (reparseable.isParsable(newTextStr, project)) {
            final ChameleonElement chameleon = (ChameleonElement)Factory.createSingleLeafElement(reparseable, newFileText,
                                                                                                 textRange.getStartOffset(),
                                                                                                 textRange.getEndOffset() + lengthShift,
                                                                                                 charTable, file.getManager(), fileImpl);
            mergeTrees(fileImpl, parent, reparseable.parseContents(chameleon).getTreeParent());
            //ChangeUtil.replaceAllChildren((CompositeElement)parent, reparseable.parseContents(chameleon).getTreeParent());
            return;
          }
          else if (reparseable instanceof IErrorCounterChameleonElementType) {
            int currentErrorLevel = ((IErrorCounterChameleonElementType)reparseable).getErrorsCount(newTextStr, project);
            if (currentErrorLevel == IErrorCounterChameleonElementType.FATAL_ERROR) {
              prevReparseable = parent;
            }
            else if (Math.abs(currentErrorLevel) < Math.abs(minErrorLevel)) {
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

      if (bestReparseable != null && !theOnlyReparseable) {
        // best reparseable available
        final ASTNode treeElement = bestReparseable;
        final TextRange textRange = treeElement.getTextRange();
        final ChameleonElement chameleon = (ChameleonElement)Factory.createLeafElement(bestReparseable.getElementType(), newFileText,
                                                                                       textRange.getStartOffset(),
                                                                                       textRange.getEndOffset() + lengthShift, -1,
                                                                                       treeFileElement.getCharTable());
        chameleon.putUserData(CharTable.CHAR_TABLE_KEY, treeFileElement.getCharTable());
        chameleon.setTreeParent((CompositeElement)parent);
        treeElement.replaceAllChildrenToChildrenOf(
          chameleon.transform(treeFileElement.getCharTable(), fileImpl.createLexer(), project).getTreeParent());
      }
      else {
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
        if (file instanceof PsiPlainTextFile) {
          fileType = StdFileTypes.PLAIN_TEXT;
        }

        final Grammar grammarByFileType = GrammarUtil.getGrammarByFileType(fileType);
        if (grammarByFileType != null && file.getLanguage() != StdLanguages.JSP && file.getLanguage() != StdLanguages.JSPX) {
          ParsingUtil.reparse(grammarByFileType, treeFileElement.getCharTable(), treeFileElement, newFileText, startOffset, endOffset,
                              lengthShift, file.getViewProvider());
        }
        else {
          makeFullParse(parent, newFileText, textLength, fileImpl, fileType);
        }
      }
    }
    finally {
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
                                            final int endOffset,
                                            final int lengthDiff,
                                            final int changedOffset) {
    final LeafElement leafElement = treeFileElement.findLeafElementAt(startOffset);
    if (leafElement == null || hasErrorElementChild(leafElement.getTreeParent()) || hasErrorElementChild(leafElement.getTreeNext()) ||
        hasErrorElementChild(leafElement.getTreePrev())) {
      return false;
    }
    if (!leafElement.getTextRange().contains(new TextRange(startOffset, endOffset))) return false;
    final LeafElement leafElementToChange = treeFileElement.findLeafElementAt(changedOffset);
    if (leafElementToChange == null) return false;
    TextRange leafRangeToChange = leafElementToChange.getTextRange();
    LeafElement newElement = Factory.createLeafElement(leafElementToChange.getElementType(), newFileText,
                                                       leafRangeToChange.getStartOffset(), leafRangeToChange.getEndOffset() + lengthDiff,
                                                       -1, treeFileElement.getCharTable());
    newElement.putUserData(CharTable.CHAR_TABLE_KEY, treeFileElement.getCharTable());
    ChangeUtil.replaceChild(leafElementToChange.getTreeParent(), leafElementToChange, newElement);
    return true;
  }

  private static void makeFullParse(ASTNode parent, char[] newFileText, int textLength, final PsiFileImpl fileImpl, FileType fileType) {
    if (parent instanceof CodeFragmentElement) {
      final FileElement holderElement = new DummyHolder(fileImpl.getManager(), null).getTreeElement();
      TreeUtil.addChildren(holderElement, fileImpl.createContentLeafElement(newFileText, 0, textLength, holderElement.getCharTable()));
      parent.replaceAllChildrenToChildrenOf(holderElement);
    }
    else {
      final PsiManagerImpl manager = (PsiManagerImpl)fileImpl.getManager();
      final PsiElementFactoryImpl factory = (PsiElementFactoryImpl)manager.getElementFactory();
      final CharArrayCharSequence seq = new CharArrayCharSequence(newFileText, 0, textLength);
      final PsiFileImpl newFile =
        (PsiFileImpl)factory.createFileFromText(fileImpl.getName(), fileType, seq, fileImpl.getModificationStamp(), true, false);
      newFile.setOriginalFile(fileImpl);

      final FileElement newFileElement = (FileElement)newFile.getNode();
      final FileElement oldFileElement = (FileElement)fileImpl.getNode();

      if (false) { // TODO: Just to switch off incremental tree patching for certain conditions (like languages) if necessary.
        replaceFileElement(fileImpl, oldFileElement, newFileElement, manager);
      }
      else {
        mergeTrees(fileImpl, oldFileElement, newFileElement);
      }
    }
  }

  private static void replaceFileElement(final PsiFileImpl fileImpl, final FileElement fileElement,
                                         final FileElement newFileElement,
                                         final PsiManagerImpl manager) {
    final RepositoryManager repositoryManager = manager.getRepositoryManager();
    final int oldLength = fileElement.getTextLength();
    sendPsiBeforeEvent(fileImpl);
    if (repositoryManager != null) repositoryManager.beforeChildAddedOrRemoved(fileImpl, fileElement);
    if (fileElement.getFirstChildNode() != null) TreeUtil.removeRange(fileElement.getFirstChildNode(), null);
    final ASTNode firstChildNode = newFileElement.getFirstChildNode();
    if (firstChildNode != null) TreeUtil.addChildren(fileElement, (TreeElement)firstChildNode);
    fileImpl.getTreeElement().setCharTable(newFileElement.getCharTable());
    if (repositoryManager != null) repositoryManager.beforeChildAddedOrRemoved(fileImpl, fileElement);
    manager.invalidateFile(fileImpl);
    fileElement.subtreeChanged();
    sendPsiAfterEvent(fileImpl, oldLength);
  }

  public static class DiffBuilder implements DiffTreeChangeBuilder<ASTNode, ASTNode> {
    private final RepositoryManager myRepositoryManager;
    private final TreeChangeEventImpl myEvent;
    private final PsiFileImpl myFile;
    private final PsiManagerImpl myPsiManager;
    private final boolean myIsPhysicalScope;


    public DiffBuilder(final PsiFileImpl fileImpl) {
      myFile = fileImpl;
      myIsPhysicalScope = fileImpl.isPhysical();
      myPsiManager = (PsiManagerImpl)fileImpl.getManager();
      myRepositoryManager = myPsiManager.getRepositoryManager();
      myEvent = new TreeChangeEventImpl(fileImpl.getProject().getModel().getModelAspect(TreeAspect.class), fileImpl.getTreeElement());
    }

    public void nodeReplaced(final ASTNode oldNode, final ASTNode newNode) {
      if (oldNode instanceof FileElement && newNode instanceof FileElement) {
        replaceFileElement(myFile, (FileElement)oldNode, (FileElement)newNode, myPsiManager);
      }
      else {
        myRepositoryManager.beforeChildAddedOrRemoved(myFile, oldNode);

        TreeUtil.remove((TreeElement)newNode);
        TreeUtil.replaceWithList((TreeElement)oldNode, (TreeElement)newNode);

        final ReplaceChangeInfoImpl change = (ReplaceChangeInfoImpl)ChangeInfoImpl.create(ChangeInfo.REPLACE, newNode);
        change.setReplaced(oldNode);
        myEvent.addElementaryChange(newNode, change);
        ((TreeElement)newNode).clearCaches();
        if (!(newNode instanceof FileElement)) {
          ((CompositeElement)newNode.getTreeParent()).subtreeChanged();
        }
        myRepositoryManager.beforeChildAddedOrRemoved(myFile, newNode);

        //System.out.println("REPLACED: " + oldNode + " to " + newNode);
      }
    }

    public void nodeDeleted(ASTNode parent, final ASTNode child) {
      myRepositoryManager.beforeChildAddedOrRemoved(myFile, parent);

      PsiElement psiParent = parent.getPsi();
      PsiElement psiChild = myIsPhysicalScope && !(child instanceof ChameleonElement) ? child.getPsi() : null;

      PsiTreeChangeEventImpl event = null;
      if (psiParent != null && psiChild != null) {
        event = new PsiTreeChangeEventImpl(myPsiManager);
        event.setParent(psiParent);
        event.setChild(psiChild);
        myPsiManager.beforeChildRemoval(event);
      }

      myEvent.addElementaryChange(child, ChangeInfoImpl.create(ChangeInfo.REMOVED, child));
      TreeUtil.remove((TreeElement)child);
      ((CompositeElement)parent).subtreeChanged();

      /*if (event != null) {
        myPsiManager.childRemoved(event);
      }*/

      //System.out.println("DELETED from " + parent + ": " + child);
    }

    public void nodeInserted(final ASTNode oldParent, final ASTNode node, final int pos) {
      myRepositoryManager.beforeChildAddedOrRemoved(myFile, oldParent);

      ASTNode anchor = null;
      for (int i = 0; i < pos; i++) {
        if (anchor == null) {
          anchor = oldParent.getFirstChildNode();
        }
        else {
          anchor = anchor.getTreeNext();
        }
      }

      TreeUtil.remove((TreeElement)node);
      if (anchor != null) {
        TreeUtil.insertAfter((TreeElement)anchor, (TreeElement)node);
      }
      else {
        if (oldParent.getFirstChildNode() != null) {
          TreeUtil.insertBefore((TreeElement)oldParent.getFirstChildNode(), (TreeElement)node);
        }
        else {
          TreeUtil.addChildren((CompositeElement)oldParent, (TreeElement)node);
        }
      }

      myEvent.addElementaryChange(node, ChangeInfoImpl.create(ChangeInfo.ADD, node));
      ((TreeElement)node).clearCaches();
      ((CompositeElement)oldParent).subtreeChanged();

      myRepositoryManager.beforeChildAddedOrRemoved(myFile, oldParent);
      //System.out.println("INSERTED to " + oldParent + ": " + node + " at " + pos);
    }

    public TreeChangeEventImpl getEvent() {
      return myEvent;
    }
  }

  private static void mergeTrees(final PsiFileImpl file, final ASTNode oldRoot, final ASTNode newRoot) {
    //System.out.println("---------------------------------------------------");
    synchronized (PsiLock.LOCK) {
      if (newRoot instanceof FileElement) {
        ((FileElement)newRoot).setCharTable(file.getTreeElement().getCharTable());
      }

      ChameleonTransforming.transformChildren(newRoot);
      ChameleonTransforming.transformChildren(oldRoot, true);

      final PomModel model = file.getProject().getModel();
      try {
        model.runTransaction(new PomTransactionBase(file, model.getModelAspect(TreeAspect.class)) {
          public PomModelEvent runInner() throws IncorrectOperationException {
            final DiffBuilder builder = new DiffBuilder(file);
            DiffTree.diff(new ASTDiffTreeStructure(oldRoot), new ASTDiffTreeStructure(newRoot), new ASTShallowComparator(), builder);
            file.subtreeChanged();

            return new TreeAspectEvent(model, builder.getEvent());
          }
        });
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
      catch (Throwable th) {
        LOG.error(th);
      }
      finally {
        ((PsiManagerImpl)file.getManager()).invalidateFile(file);
      }
    }
  }

  private static void sendPsiAfterEvent(final PsiFileImpl scope, int oldLength) {
    if (!scope.isPhysical()) return;
    final PsiManagerImpl manager = (PsiManagerImpl)scope.getManager();
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(manager);
    event.setParent(scope);
    event.setFile(scope);
    event.setOffset(0);
    event.setOldLength(oldLength);
    manager.childrenChanged(event);
  }

  private static void sendPsiBeforeEvent(final PsiFile scope) {
    if (!scope.isPhysical()) return;
    final PsiManagerImpl manager = (PsiManagerImpl)scope.getManager();
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(manager);
    event.setParent(scope);
    event.setFile(scope);
    event.setOffset(0);
    event.setOldLength(scope.getTextLength());
    manager.beforeChildrenChange(event);
  }
}
