package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SyntheticElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class BaseDelombokAction extends AnAction {
  private final BaseDelombokHandler myHandler;

  protected BaseDelombokAction(BaseDelombokHandler handler) {
    myHandler = handler;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final Project project = event.getProject();
    if (project == null) {
      return;
    }

    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    if (psiDocumentManager.hasUncommitedDocuments()) {
      psiDocumentManager.commitAllDocuments();
    }

    final DataContext dataContext = event.getDataContext();
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);

    if (null != editor) {
      final PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
      if (null != psiFile) {
        final PsiClass targetClass = getTargetClass(editor, psiFile);
        if (null != targetClass) {
          process(project, psiFile, targetClass);
        }
      }
    } else {
      final VirtualFile[] files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
      if (null != files) {
        for (VirtualFile file : files) {
          if (file.isDirectory()) {
            processDirectory(project, file);
          } else {
            processFile(project, file);
          }
        }
      }
    }
  }

  private void processDirectory(@NotNull final Project project, @NotNull VirtualFile vFile) {
    VfsUtilCore.visitChildrenRecursively(vFile, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (!file.isDirectory()) {
          processFile(project, file);
        }
        return true;
      }
    });
  }

  private void processFile(Project project, VirtualFile file) {
    if (StdFileTypes.JAVA.equals(file.getFileType())) {
      final PsiManager psiManager = PsiManager.getInstance(project);
      PsiJavaFile psiFile = (PsiJavaFile) psiManager.findFile(file);
      if (psiFile != null) {
        process(project, psiFile);
      }
    }
  }

  protected void process(@NotNull final Project project, @NotNull final PsiJavaFile psiJavaFile) {
    executeCommand(project, new Runnable() {
      @Override
      public void run() {
        getHandler().invoke(project, psiJavaFile);
      }
    });
  }

  protected void process(@NotNull final Project project, @NotNull final PsiFile psiFile, @NotNull final PsiClass psiClass) {
    executeCommand(project, new Runnable() {
      @Override
      public void run() {
        getHandler().invoke(project, psiFile, psiClass);
      }
    });
  }

  private void executeCommand(final Project project, final Runnable action) {
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(action);
      }
    }, getCommandName(), null);
  }

  private BaseDelombokHandler getHandler() {
    return myHandler;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final DataContext dataContext = event.getDataContext();

    final Project project = event.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (null != editor) {
      final PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
      presentation.setEnabled(file != null && isValidForFile(editor, file));
      return;
    }

    boolean isValid = false;
    final VirtualFile[] files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    if (null != files) {
      PsiManager psiManager = PsiManager.getInstance(project);
      for (VirtualFile file : files) {
        if (file.isDirectory()) {
          //directory is valid
          isValid = true;
          break;
        }
        if (StdFileTypes.JAVA.equals(file.getFileType())) {
          PsiJavaFile psiFile = (PsiJavaFile) psiManager.findFile(file);
          if (psiFile != null) {
            for (PsiClass psiClass : psiFile.getClasses()) {
              if (isValidForClass(psiClass)) {
                isValid = true;
                break;
              }

              for (PsiClass innerClass : psiClass.getAllInnerClasses()) {
                if (isValidForClass(innerClass)) {
                  isValid = true;
                  break;
                }
              }
            }
          }
        }
        if (isValid) {
          break;
        }
      }
    }
    presentation.setEnabled(isValid);
  }

  private boolean isValidForClass(@NotNull PsiClass targetClass) {
    if (!targetClass.isInterface()) {
      Collection<PsiAnnotation> psiAnnotations = myHandler.collectProcessableAnnotations(targetClass);
      return !psiAnnotations.isEmpty();
    }
    return false;
  }

  @Nullable
  private PsiClass getTargetClass(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) {
      return null;
    }
    final PsiClass target = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    return target instanceof SyntheticElement ? null : target;
  }

  private boolean isValidForFile(@NotNull Editor editor, @NotNull PsiFile file) {
    if (!(file instanceof PsiJavaFile)) {
      return false;
    }
    if (file instanceof PsiCompiledElement) {
      return false;
    }
    if (!file.isWritable()) {
      return false;
    }

    PsiClass targetClass = getTargetClass(editor, file);
    return targetClass != null && isValidForClass(targetClass);
  }

  private String getCommandName() {
    String text = getTemplatePresentation().getText();
    return text == null ? "" : text;
  }
}
