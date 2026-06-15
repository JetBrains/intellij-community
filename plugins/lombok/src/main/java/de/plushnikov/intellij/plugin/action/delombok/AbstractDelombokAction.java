package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSet;
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
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public abstract class AbstractDelombokAction extends AnAction {
  private DelombokHandler myHandler;

  protected AbstractDelombokAction() {
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  protected abstract DelombokHandler createHandler();

  private DelombokHandler getHandler() {
    if (null == myHandler) {
      myHandler = createHandler();
    }
    return myHandler;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final Project project = event.getProject();
    if (project == null) {
      return;
    }

    final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    psiDocumentManager.commitAllDocuments();

    final DataContext dataContext = event.getDataContext();
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);

    if (null != editor) {
      final PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
      if (null != psiFile) {
        final PsiClass targetClass = getTargetClass(editor, psiFile);
        if (null != targetClass) {
          process(project, psiFile, targetClass);
        }
      }
    }
    else {
      final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
      if (null != files && files.length > 0) {
        collectJavaFilesAndProcessThem(project, files);
      }
    }
  }

  private void collectJavaFilesAndProcessThem(Project project, VirtualFile @NotNull [] selectedFilesAndDirs) {
    var task = new Task.Backgroundable(project, LombokBundle.message("group.DelombokActionGroup.text"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        List<PsiFile> files = ReadAction.computeBlocking(() -> {
          indicator.setIndeterminate(true);
          return collectJavaFilesToProcess(project, selectedFilesAndDirs);
        });
        processJavaFilesWithModalProgress(files, project);
      }
    };
    task.queue();
  }

  private static @NotNull List<PsiFile> collectJavaFilesToProcess(@NotNull Project project,
                                                                 VirtualFile @NotNull [] selectedFilesAndDirs) {
    VirtualFileSet virtualFiles = VfsUtilCore.createCompactVirtualFileSet();
    for (VirtualFile file : selectedFilesAndDirs) {
      ProgressManager.checkCanceled();
      if (file.isDirectory()) {
        VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
          @Override
          public boolean visitFile(@NotNull VirtualFile file) {
            ProgressManager.checkCanceled();
            if (!file.isDirectory()) {
              addFileIfJava(file, virtualFiles);
            }
            return true;
          }
        });
      }
      else {
        addFileIfJava(file, virtualFiles);
      }
    }
    return toPsiFiles(project, virtualFiles);
  }

  private static @NotNull List<PsiFile> toPsiFiles(@NotNull Project project, @NotNull VirtualFileSet virtualFiles) {
    PsiManager psiManager = PsiManager.getInstance(project);
    return ContainerUtil.mapNotNull(virtualFiles, virtualFile -> {
      ProgressManager.checkCanceled();
      return psiManager.findFile(virtualFile);
    });
  }

  private void processJavaFilesWithModalProgress(@NotNull List<PsiFile> psiFiles, Project project) {
    if (ContainerUtil.isEmpty(psiFiles)) return;
    String progressTitle = LombokBundle.message("group.DelombokActionGroup.text");
    SequentialModalProgressTask progressTask = new SequentialModalProgressTask(project, progressTitle, true);
    progressTask.setTask(new ProcessingSequentialTask(psiFiles, project));
    progressTask.queue();
  }

  private static void addFileIfJava(@NotNull VirtualFile virtualFile, @NotNull VirtualFileSet result) {
    if (!JavaFileType.INSTANCE.equals(virtualFile.getFileType())) {
      return;
    }
    result.add(virtualFile);
  }

  private void process(@NotNull Project project, @NotNull PsiFile psiFile) {
    executeCommand(project, () -> {
      if (!psiFile.isValid()) {
        return;
      }
      if (commitDocument(project, psiFile)) return;
      if (psiFile instanceof PsiJavaFile psiJavaFile) {
        getHandler().invoke(project, psiJavaFile);
      }
    });
  }

  private static boolean commitDocument(@NotNull Project project, @NotNull PsiFile psiFile) {
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    Document document = psiDocumentManager.getDocument(psiFile);
    if (document == null) {
      return true;
    }
    psiDocumentManager.commitDocument(document);
    return false;
  }

  private void process(final @NotNull Project project, final @NotNull PsiFile psiFile, final @NotNull PsiClass psiClass) {
    executeCommand(project, () -> getHandler().invoke(project, psiFile, psiClass));
  }

  private void executeCommand(final Project project, final Runnable action) {
    CommandProcessor.getInstance().executeCommand(project,
      () -> ApplicationManager.getApplication().runWriteAction(action), getCommandName(), null);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final DataContext dataContext = event.getDataContext();

    final Project project = event.getProject();
    if (project == null || !LombokLibraryUtil.hasLombokLibrary(project)) {
      presentation.setEnabled(false);
      return;
    }

    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (null != editor) {
      final PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
      presentation.setEnabled(file != null && isValidForFile(editor, file));
      return;
    }

    boolean isValid = false;
    final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    if (null != files) {
      PsiManager psiManager = PsiManager.getInstance(project);
      for (VirtualFile file : files) {
        if (file.isDirectory()) {
          //directory is valid
          isValid = true;
          break;
        }
        if (JavaFileType.INSTANCE.equals(file.getFileType())) {
          PsiJavaFile psiFile = (PsiJavaFile) psiManager.findFile(file);
          if (psiFile != null) {
            isValid = ContainerUtil.or(psiFile.getClasses(), this::isValidForClass);
          }
        }
        if (isValid) {
          break;
        }
      }
    }
    presentation.setEnabled(isValid);
  }

  private boolean isValidForClass(@NotNull PsiClass psiClass) {
    if (psiClass.isInterface()) {
      return false;
    }
    Collection<PsiAnnotation> psiAnnotations = getHandler().collectProcessableAnnotations(psiClass);
    if (!psiAnnotations.isEmpty()) {
      return true;
    }
    final Collection<PsiClass> classesIntern = PsiClassUtil.collectInnerClassesIntern(psiClass);
    return ContainerUtil.exists(classesIntern, this::isValidForClass);
  }

  private static @Nullable PsiClass getTargetClass(Editor editor, PsiFile file) {
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

  private @NlsContexts.Command String getCommandName() {
    String text = getTemplatePresentation().getText();
    return text == null ? "" : text;
  }

  @NotNullByDefault
  private class ProcessingSequentialTask implements SequentialTask {
    private final Project project;
    private final Iterator<PsiFile> iterator;
    private final int fileCount;
    private int processedFiles;

    private ProcessingSequentialTask(List<PsiFile> files, Project project) {
      this.project = project;
      this.iterator = files.iterator();
      this.fileCount = files.size();
    }

    @Override
    public boolean iteration() {
      return iteration(null);
    }

    @Override
    public boolean iteration(@Nullable ProgressIndicator indicator) {
      if (!iterator.hasNext()) {
        return true;
      }
      PsiFile javaFile = iterator.next();
      if (javaFile.isValid()) {
        process(project, javaFile);
      }
      processedFiles++;
      if (indicator != null) {
        indicator.setText(javaFile.getName());
        indicator.setFraction((double)processedFiles / fileCount);
      }
      return isDone();
    }

    @Override
    public void stop() {
      processedFiles = fileCount;
    }

    @Override
    public boolean isDone() {
      return processedFiles >= fileCount;
    }
  }
}
