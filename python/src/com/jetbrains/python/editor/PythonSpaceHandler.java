package com.jetbrains.python.editor;

import com.intellij.codeInsight.template.impl.editorActions.SpaceHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class PythonSpaceHandler extends SpaceHandler {
  public PythonSpaceHandler(TypedActionHandler originalHandler) {
    super(originalHandler);
  }

  public void execute(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
    super.execute(editor, charTyped, dataContext);
    if (charTyped != ' ') {
      return;
    }

    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    VirtualFile vfile = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (project != null && vfile != null) {
      PsiFile file = PsiManager.getInstance(project).findFile(vfile);
      if (file != null) {
        PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
        if (PythonDocCommentUtil.inDocComment(element)) {
          PythonDocumentationProvider provider = new PythonDocumentationProvider();
          PyFunction fun = PsiTreeUtil.getParentOfType(element, PyFunction.class);
          if (fun != null) {
            String docStub = provider.generateDocumentationContentStub(fun, false);
            docStub += element.getParent().getText().substring(0,3);
            if (docStub != null && docStub.length() != 0) {
              editor.getDocument().insertString(editor.getCaretModel().getOffset(), docStub);
              editor.getCaretModel().moveCaretRelatively(100, 1, false, false, false);
              return;
            }
          }
          PyElement klass = PsiTreeUtil.getParentOfType(element, PyClass.class, PyFile.class);
          if (klass != null) {
            editor.getDocument().insertString(editor.getCaretModel().getOffset(),
                            PythonDocCommentUtil.generateDocForClass(klass, element.getParent().getText().substring(0,3)));
            return;
          }
        }
      }
    }
  }
}
