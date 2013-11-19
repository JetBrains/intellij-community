package org.jetbrains.postfixCompletion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;

public final class ExpandPostfixEditorActionHandler extends EditorActionHandler {
  @NotNull private final EditorActionHandler myUnderlyingHandler;
  @NotNull private final PostfixTemplatesManager myTemplatesManager;

  public ExpandPostfixEditorActionHandler(
      @NotNull EditorActionHandler underlyingHandler, @NotNull PostfixTemplatesManager templatesManager) {
    myTemplatesManager = templatesManager;
    myUnderlyingHandler = underlyingHandler;
  }

  @Override public boolean isEnabled(@NotNull Editor editor, @NotNull DataContext dataContext) {
    if (findPostfixTemplate(editor, false) != null)
      return true;

    return myUnderlyingHandler.isEnabled(editor, dataContext);
  }

  @Override public void execute(@NotNull Editor editor, @NotNull DataContext dataContext) {
    final LookupElement postfixElement = findPostfixTemplate(editor, true);
    if (postfixElement == null) {
      myUnderlyingHandler.execute(editor, dataContext);
      return;
    }

    Project project = editor.getProject();
    assert (project != null) : "project != null";

    Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(document);

    PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
    assert (psiFile != null) : "psiFile != null";

    OffsetMap offsetMap = new OffsetMap(document);
    int startOffset = editor.getCaretModel().getOffset() - postfixElement.getLookupString().length();
    offsetMap.addOffset(CompletionInitializationContext.START_OFFSET, startOffset);

    final InsertionContext insertionContext = new InsertionContext(
      offsetMap, '\t', new LookupElement[]{postfixElement}, psiFile, editor, false);

    Application application = ApplicationManager.getApplication();
    application.runWriteAction(new Runnable() {
      @Override public void run() { postfixElement.handleInsert(insertionContext); }
    });

    Runnable laterRunnable = insertionContext.getLaterRunnable();
    if (laterRunnable != null) {
      if (application.isUnitTestMode()) {
        laterRunnable.run();
      } else {
        application.invokeLater(laterRunnable);
      }
    }
  }

  @Nullable protected LookupElement findPostfixTemplate(@NotNull Editor editor, boolean leaveReparseOnSuccess) {
    Project project = editor.getProject();
    if (project == null) return null;

    final Document document = editor.getDocument();
    final int offset = editor.getCaretModel().getOffset();
    final String dummyIdentifier = CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED;

    boolean reparseBack = true;
    Application application = ApplicationManager.getApplication();
    try {
      // ugly physical reparse with dummy identifier
      application.runWriteAction(new Runnable() {
        @Override public void run() { document.insertString(offset, dummyIdentifier); }
      });

      PsiDocumentManager.getInstance(project).commitDocument(document);

      PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
      if (psiFile == null) return null;

      PsiElement position = psiFile.findElementAt(offset);
      if (position == null) return null;

      // fine, now let's build contexts and look for postfix templates
      PostfixExecutionContext executionContext =
        new PostfixExecutionContext(true, dummyIdentifier, (psiFile instanceof PsiCodeFragment));

      PostfixTemplateContext templateContext = myTemplatesManager.isAvailable(position, executionContext);
      if (templateContext == null) return null;

      for (LookupElement element : myTemplatesManager.collectTemplates(templateContext)) {
        String lookupString = element.getLookupString();
        if (offset <= lookupString.length()) continue;

        TextRange prefixRange = new TextRange(offset - lookupString.length(), offset);
        if (lookupString.equals(document.getText(prefixRange))) {
          if (leaveReparseOnSuccess) reparseBack = false;
          return element;
        }
      }
    } finally {
      if (reparseBack) {
        application.runWriteAction(new Runnable() {
          @Override public void run() {
            document.deleteString(offset, offset + dummyIdentifier.length());
          }
        });
      }
    }

    return null;
  }
}
