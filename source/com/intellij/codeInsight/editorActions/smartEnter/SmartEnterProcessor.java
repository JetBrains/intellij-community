package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 3:31:23 PM
 * To change this template use Options | File Templates.
 */
public class SmartEnterProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor");
  private static final List<Fixer> ourFixers = new ArrayList<Fixer>();
  private static final List<EnterProcessor> ourEnterProcessors = new ArrayList<EnterProcessor>();
  static {
    ourFixers.add(new LiteralFixer());
    ourFixers.add(new MethodCallFixer());
    ourFixers.add(new IfConditionFixer());
    ourFixers.add(new WhileConditionFixer());
    ourFixers.add(new DoWhileConditionFixer());
    ourFixers.add(new BlockBraceFixer());
    ourFixers.add(new MissingIfBranchesFixer());
    ourFixers.add(new MissingWhileBodyFixer());
    ourFixers.add(new MissingForBodyFixer());
    ourFixers.add(new MissingForeachBodyFixer());
    ourFixers.add(new MissingMethodBodyFixer());
    ourFixers.add(new ParameterListFixer());
    ourFixers.add(new MissingReturnExpressionFixer());
    ourFixers.add(new MissingThrowExpressionFixer());
    ourFixers.add(new ParenthesizedFixer());
    ourFixers.add(new SemicolonFixer());
    ourFixers.add(new EnumFieldFixer());
    //ourFixers.add(new CompletionFixer());


    ourEnterProcessors.add(new ValidLiteralBreakerEnterProcessor());
    ourEnterProcessors.add(new CommentBreakerEnterProcessor());
    ourEnterProcessors.add(new AfterSemicolonEnterProcessor());
    ourEnterProcessors.add(new BreakingControlFlowEnterProcessor());
    ourEnterProcessors.add(new PlainEnterProcessor());
  }

  private Project myProject;
  private Editor myEditor;
  private PsiFile myPsiFile;
  private long myStartTimestamp;
  private int myFirstErrorOffset = Integer.MAX_VALUE;

  public SmartEnterProcessor(Project project, Editor editor, PsiFile psiFile) {
    myProject = project;
    myEditor = editor;
    myPsiFile = psiFile;
    myStartTimestamp = myEditor.getDocument().getModificationStamp();
  }

  public void process() {
    try {
      commit();
      myFirstErrorOffset = Integer.MAX_VALUE;

      PsiElement atCaret = getStatementAtCaret();
      if (atCaret == null) {
        if (!new CommentBreakerEnterProcessor().doEnter(myEditor, myPsiFile, false)) {
          plainEnter();
        }
        return;
      }

      List<PsiElement> queue = new ArrayList<PsiElement>();
      collectAllElements(atCaret, queue, true);
      queue.add(atCaret);
      for (int i = 0; i < queue.size(); i++) {
        PsiElement psiElement = queue.get(i);
        for (int j = 0; j < ourFixers.size(); j++) {
          Fixer fixer = ourFixers.get(j);
          fixer.apply(myEditor, this, psiElement);
          if (myEditor.getUserData(LookupImpl.LOOKUP_IN_EDITOR_KEY) != null) return;
          if (isUncommited() || !psiElement.isValid()) {
            process();
            return;
          }
        }
      }

      doEnter(atCaret);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void doEnter(PsiElement atCaret) throws IncorrectOperationException {
    if (myFirstErrorOffset != Integer.MAX_VALUE) {
      myEditor.getCaretModel().moveToOffset(myFirstErrorOffset);
      reformat(atCaret);
      return;
    }

    reformat(atCaret);

    for (int i = 0; i < ourEnterProcessors.size(); i++) {
      EnterProcessor processor = ourEnterProcessors.get(i);
      if (processor.doEnter(myEditor, atCaret, isModified())) return;
    }

    if (!isModified()) {
      plainEnter();
    } else {
      if (myFirstErrorOffset == Integer.MAX_VALUE) {
        myEditor.getCaretModel().moveToOffset(end(atCaret));
      } else {
        myEditor.getCaretModel().moveToOffset(myFirstErrorOffset);
      }
    }
  }

  private void reformat(PsiElement atCaret) throws IncorrectOperationException {
    PsiElement parent = atCaret.getParent();
    if (parent instanceof PsiCodeBlock) {
      final PsiCodeBlock block = (PsiCodeBlock) parent;
      if (block.getStatements().length > 0 && block.getStatements()[0] == atCaret) {
        atCaret = block;
      }
    }
    else if (parent instanceof PsiForStatement) {
      atCaret = parent;
    }

    CodeStyleManager.getInstance(myProject).reformat(atCaret);
  }

  private void collectAllElements(PsiElement atCaret, List<PsiElement> res, boolean recurse) {
    res.add(0, atCaret);
    if (doNotStepInto(atCaret)) {
      if (!recurse) return;
      recurse = false;
    }

    final PsiElement[] children = atCaret.getChildren();
    for (int i = 0; i < children.length; i++) {
      collectAllElements(children[i], res, recurse);
    }
  }

  private boolean doNotStepInto(PsiElement element) {
    return element instanceof PsiClass || element instanceof PsiCodeBlock || element instanceof PsiStatement;
  }

  public void registerUnresolvedError(int offset) {
    if (myFirstErrorOffset > offset) {
      myFirstErrorOffset = offset;
    }
  }

  private static int end(PsiElement p) {
    return p.getTextRange().getEndOffset();
  }

  private boolean isModified() {
    return myEditor.getDocument().getModificationStamp() != myStartTimestamp;
  }

  private boolean isUncommited() {
    return PsiDocumentManager.getInstance(myProject).hasUncommitedDocuments();
  }

  private void commit() {
    PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument());
  }

  private void plainEnter() {
    getEnterHandler().execute(myEditor, ((EditorEx) myEditor).getDataContext());
  }

  private EditorActionHandler getEnterHandler() {
    EditorActionHandler enterHandler = EditorActionManager.getInstance().getActionHandler(
        IdeActions.ACTION_EDITOR_START_NEW_LINE
    );
    return enterHandler;
  }

  private PsiElement getStatementAtCaret() {
    int caret = myEditor.getCaretModel().getOffset();
    return getStatementAtCaret(myEditor, caret, myPsiFile);
  }

  private PsiElement getStatementAtCaret(Editor editor, int caret, PsiFile psiFile) {
    final Document doc = editor.getDocument();
    CharSequence chars = doc.getCharsSequence();
    int offset = CharArrayUtil.shiftBackward(chars, caret - 1, " \t");
    if (doc.getLineNumber(offset) < doc.getLineNumber(caret)) {
      offset = CharArrayUtil.shiftForward(chars, caret, " \t");
    }

    PsiElement atCaret = psiFile.findElementAt(offset);
    if (atCaret instanceof PsiWhiteSpace) return null;
    if (atCaret instanceof PsiJavaToken && "}".equals(atCaret.getText())) return null;

    PsiElement statementAtCaret = PsiTreeUtil.getParentOfType(atCaret,
                                                              new Class[]{PsiStatement.class, PsiCodeBlock.class,
                                                                          PsiMember.class,
                                                                          PsiComment.class},
                                                              false);

    if (statementAtCaret instanceof PsiBlockStatement) return null;

    if (statementAtCaret != null && statementAtCaret.getParent() instanceof PsiForStatement) statementAtCaret = statementAtCaret.getParent();

    return statementAtCaret instanceof PsiStatement || statementAtCaret instanceof PsiMember
           ? statementAtCaret
           : null;
  }
}
