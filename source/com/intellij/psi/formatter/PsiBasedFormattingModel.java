package com.intellij.psi.formatter;

import com.intellij.codeFormatting.general.FormatterUtil;
import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.FormattingModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.events.impl.TreeChangeEventImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.util.IncorrectOperationException;

public class PsiBasedFormattingModel implements FormattingModel{
  private final Document myDocument;
  private final ASTNode myASTNode;
  private final Project myProject;
  private PomModelEvent myPomEvent;

  public PsiBasedFormattingModel(final PsiFile file) {
    myASTNode = SourceTreeToPsiMap.psiElementToTree(file);
    myProject = file.getProject();
    myDocument = PsiDocumentManager.getInstance(myProject).getDocument(file);
  }

  public int getLineNumber(int offset) {
    return myDocument.getLineNumber(offset);
  }

  public int getLineStartOffset(int line) {
    return myDocument.getLineStartOffset(line);
  }

  public void runModificationTransaction(final Runnable action) throws IncorrectOperationException {
    final PomModel model = myProject.getModel();
    final TreeAspect aspect = model.getModelAspect(TreeAspect.class);
    try {
      model.runTransaction(new PomTransactionBase(SourceTreeToPsiMap.treeElementToPsi(myASTNode)) {
        public PomModelEvent run(){
          myPomEvent = new PomModelEvent(model);
          final FileElement fileElement = getFileElement(myASTNode);
          myPomEvent.registerChangeSet(aspect, new TreeChangeEventImpl(aspect, fileElement));
          action.run();
          TreeUtil.clearCaches(fileElement);
          return myPomEvent;
        }

        private FileElement getFileElement(final ASTNode element) {
          return (FileElement)SourceTreeToPsiMap.psiElementToTree(SourceTreeToPsiMap.treeElementToPsi(element).getContainingFile());
        }
      }, aspect);
    }
    catch (IncorrectOperationException e) {
      throw e;
    }
    myPomEvent = null;

  }

  public void replaceWhiteSpace(TextRange textRange, String whiteSpace) {
    FormatterUtil.replaceWhiteSpace(whiteSpace,
                                    myASTNode.findLeafElementAt(textRange.getEndOffset()),
                                    ElementType.WHITE_SPACE, myPomEvent);
  }

  public CharSequence getText(final TextRange textRange) {
    return myDocument.getCharsSequence();
  }
}
