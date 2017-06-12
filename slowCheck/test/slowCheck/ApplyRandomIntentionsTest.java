package slowCheck;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ApplyRandomIntentionsTest extends AbstractApplyAndRevertTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initCompiler();
  }

  public void testOpenFilesAndRevert() throws Throwable {
    doOpenFilesAndRevert(psiFile -> true);
  }

  public void testOpenFilesAndRevertDeleteForeachInitializers() throws Throwable {
    doOpenFilesAndRevert(psiFile -> {
      WriteCommandAction.runWriteCommandAction(myProject, () -> PsiTreeUtil.findChildrenOfType(psiFile, PsiForStatement.class).stream()
        .limit(20)
        .forEach(stmt -> stmt.getInitialization().delete()));
      return false;
    });
  }

  public void testOpenFilesAndRevertDeleteSecondArgument() throws Throwable {
    doOpenFilesAndRevert(psiFile -> {
      WriteCommandAction.runWriteCommandAction(myProject, () -> PsiTreeUtil.findChildrenOfType(psiFile, PsiCallExpression.class)
        .stream()
        .filter(PsiElement::isValid)
        .map(PsiCall::getArgumentList)
        .filter(Objects::nonNull)
        .filter(argList -> argList.getExpressions().length > 1)
        .limit(20)
        .forEach(argList -> {
          if (!argList.isValid()) return;
          PsiExpression arg = argList.getExpressions()[1];
          if (!arg.isValid()) return;
          arg.delete();
        }));
      return false;
    });
  }

  public void testOpenFilesAndRevertAddNullArgument() throws Throwable {
    doOpenFilesAndRevert(psiFile -> {
      WriteCommandAction.runWriteCommandAction(myProject,
                                                      () -> PsiTreeUtil.findChildrenOfType(psiFile, PsiMethodCallExpression.class).stream()
                                                        .filter(PsiElement::isValid)
                                                        .filter(call -> call.getArgumentList().getExpressions().length > 1)
                                                        .forEach(call -> call.getArgumentList().add(JavaPsiFacade.getElementFactory(myProject).createExpressionFromText("null", call))));
      return false;
    });
  }

  public void testMakeAllMethodsVoid() throws Throwable {
    doOpenFilesAndRevert(psiFile -> {
      WriteCommandAction.runWriteCommandAction(myProject,
                                               () -> PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod.class).stream()
                                                        .filter(method -> method.getReturnTypeElement() != null)
                                                        .forEach(method -> method.getReturnTypeElement().replace(JavaPsiFacade.getElementFactory(myProject).createTypeElement(PsiType.VOID))));
      return false;
    });
  }
  
  static class InvokeIntention extends ActionOnRange {
    final int intentionIndex;
    IntentionAction intentionAction;
    
    InvokeIntention(Document document, int offset, int intentionIndex) {
      super(document, offset, offset);
      this.intentionIndex = intentionIndex;
    }

    @Override
    public String toString() {
      return "InvokeIntention{" + getStartOffset() + ", " + intentionAction + '}';
    }
  }

  private void doOpenFilesAndRevert(Function<PsiFile, Boolean> mutation) throws Throwable {
    CheckerSettings settings = CheckerSettings.DEFAULT_SETTINGS;
    Checker.forAll(settings.withIterationCount(10), javaFiles(), file -> {
      System.out.println("for file: " + file.getPresentableUrl());
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
      if (psiFile == null) return false;
      int textLength = psiFile.getTextLength();

      Editor editor = FileEditorManager.getInstance(myProject).openTextEditor(new OpenFileDescriptor(myProject, file, 0), true);
      Document document = editor.getDocument();
      Generator<InvokeIntention> genInvocation = Generator.from(
        data -> new InvokeIntention(document,
                                    Generator.integers(0, textLength).generateValue(data),
                                    Generator.integers(0, 100).generateValue(data))).noShrink();
      Checker.forAll(settings.withIterationCount(5), Generator.listOf(genInvocation), list -> {
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
        changeDocumentAndRevert(document, () -> {
          Boolean checkCompilationStatus = mutation.apply(psiFile);
          documentManager.commitAllDocuments();
          CodeInsightTestFixtureImpl.instantiateAndRun(psiFile, editor, new int[0], false);
          for (InvokeIntention invocation : list) {
            int offset = invocation.getStartOffset();
            if (offset < 0) continue;

            IntentionAction randomAction = invocation.intentionAction = getRandomIntention(psiFile, editor, offset, invocation.intentionIndex);
            if (randomAction == null) continue;

            String actionText = randomAction.getText();

            System.out.println("apply: " + actionText + " at " + offset);
            String currentFileText = psiFile.getText();
            try {
              CodeInsightTestFixtureImpl.invokeIntention(randomAction, psiFile, editor, actionText);
              assertFalse("Document is left blocked by PSI", documentManager.isDocumentBlockedByPsi(document));
              checkNoForeignDocuments(document, documentManager.getUncommittedDocuments());
              checkNoForeignDocuments(document, FileDocumentManager.getInstance().getUnsavedDocuments());
              checkStubPsiWellFormed(psiFile);
              if (psiFile.textMatches(currentFileText)) {
                fail("No change was performed: " + currentFileText);
              }
              
              if (checkCompilationStatus) checkCompiles(myCompilerTester.make());
            }
            catch (Throwable e) {
              LOG.debug("File " + file.getName() + " text before applying " + actionText + ":\n" + currentFileText);
              throw e;
            }
          }
        });
        return true;
      });

      return true;
    });
  }

  private static void checkNoForeignDocuments(Document document, Document[] documents1) {
    List<Document> documents = ContainerUtil.newArrayList(documents1);
    documents.remove(document);
    assertTrue("Foreign uncommitted documents: " + documents, documents.isEmpty());
  }

  @Override
  protected String getTestDataPath() {
    return SystemProperties.getUserHome() + "/IdeaProjects/univocity-parsers";
  }

  @Nullable
  private static IntentionAction getRandomIntention(PsiFile psiFile, Editor editor, int offset, int randomInt) {
    editor.getCaretModel().moveToOffset(offset);
    List<IntentionAction> actions =
      CodeInsightTestFixtureImpl.getAvailableIntentions(editor, psiFile)
        .stream()
        .filter(action -> action.startInWriteAction())
        .filter(action -> action.getElementToMakeWritable(psiFile) == psiFile)
        .filter(action -> !shouldSkipIntention(action.getText()))
        .collect(Collectors.toList());
    return actions.isEmpty() ? null : actions.get(randomInt % actions.size());
  }

  private static boolean shouldSkipIntention(String actionText) {
    return actionText.startsWith("Flip") ||
           actionText.startsWith("Attach annotations") ||
           actionText.startsWith("Convert to string literal") ||
           actionText.startsWith("Optimize imports") || // https://youtrack.jetbrains.com/issue/IDEA-173801
           actionText.startsWith("Make method default") || 
           actionText.contains("to custom tags") || // changes only inspection settings
           actionText.startsWith("Typo: Change to...") || // doesn't change file text
           actionText.startsWith("Detail exceptions") || // can produce uncompilable code if 'catch' section contains 'instanceof's
           actionText.startsWith("Add on demand static import") || // doesn't change file text. todo it probably should?
           actionText.startsWith("Add Javadoc") || // https://youtrack.jetbrains.com/issue/IDEA-174275
           actionText.startsWith("Insert call to super method") || // super method can declare checked exceptions, unexpected at this point
           actionText.startsWith("Unimplement");
  }
}
