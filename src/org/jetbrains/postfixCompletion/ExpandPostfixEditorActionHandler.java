package org.jetbrains.postfixCompletion;

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.OffsetMap;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.postfixCompletion.Infrastructure.PostfixExecutionContext;
import org.jetbrains.postfixCompletion.Infrastructure.PostfixTemplateContext;
import org.jetbrains.postfixCompletion.Infrastructure.PostfixTemplatesService;

public final class ExpandPostfixEditorActionHandler extends EditorActionHandler {
  @NotNull private final EditorActionHandler myUnderlyingHandler;
  @NotNull
  private final PostfixTemplatesService myTemplatesService;

  public ExpandPostfixEditorActionHandler(@NotNull EditorActionHandler underlyingHandler,
                                          @NotNull PostfixTemplatesService templatesService) {
    myTemplatesService = templatesService;
    myUnderlyingHandler = underlyingHandler;
  }

  @Override public boolean isEnabled(@NotNull Editor editor, @NotNull DataContext dataContext) {
    return findPostfixTemplate(editor, false) != null || myUnderlyingHandler.isEnabled(editor, dataContext);
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
    int caretOffset = editor.getCaretModel().getOffset();
    int startOffset = caretOffset - postfixElement.getLookupString().length();
    offsetMap.addOffset(CompletionInitializationContext.START_OFFSET, startOffset);

    final InsertionContext insertionContext = new InsertionContext(
      offsetMap, '\t', new LookupElement[]{postfixElement}, psiFile, editor, false);

    Application application = ApplicationManager.getApplication();
    application.runWriteAction(new Runnable() {
      @Override public void run() {
        postfixElement.handleInsert(insertionContext);
      }
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
    final CommandProcessor commandProcessor = CommandProcessor.getInstance();

    boolean reparseBack = true;
    Application application = ApplicationManager.getApplication();

    try {
      // ugly physical reparse with dummy identifier
      application.runWriteAction(new Runnable() {
        @Override public void run() {
          commandProcessor.runUndoTransparentAction(new Runnable() {
            @Override public void run() {
              document.insertString(offset, dummyIdentifier);
            }
          });
        }
      });

      PsiDocumentManager.getInstance(project).commitDocument(document);

      PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
      if (psiFile == null) return null;

      PsiElement position = psiFile.findElementAt(offset);
      if (position == null) return null;

      // fine, now let's build contexts and look for postfix templates
      PostfixExecutionContext executionContext =
        new PostfixExecutionContext(true, dummyIdentifier, (psiFile instanceof PsiCodeFragment));

      PostfixTemplateContext templateContext = myTemplatesService.isAvailable(position, executionContext);
      if (templateContext == null) return null;

      for (LookupElement element : myTemplatesService.collectTemplates(templateContext)) {
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
            commandProcessor.runUndoTransparentAction(new Runnable() {
              @Override public void run() {
                document.deleteString(offset, offset + dummyIdentifier.length());
              }
            });
          }
        });
      }
    }

    return null;
  }
}