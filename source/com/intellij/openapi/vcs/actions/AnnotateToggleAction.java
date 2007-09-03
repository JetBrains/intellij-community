package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutterAction;
import com.intellij.openapi.editor.TextAnnotationGutterProvider;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.localVcs.LocalVcs;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.AnnotationListener;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

/**
 * author: lesya
 */
public class AnnotateToggleAction extends ToggleAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.actions.AnnotateToggleAction");
  protected static final Key<Collection<AnnotationFieldGutter>> KEY_IN_EDITOR = Key.create("Annotations");

  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled(PeerFactory.getInstance().getVcsContextFactory().createContextOn(e)));
  }

  private static boolean isEnabled(final VcsContext context) {
    VirtualFile[] selectedFiles = context.getSelectedFiles();
    if (selectedFiles == null) return false;
    if (selectedFiles.length != 1) return false;
    VirtualFile file = selectedFiles[0];
    if (file.isDirectory()) return false;
    Project project = context.getProject();
    if (project == null) return false;
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
    if (vcs == null) return false;
    final AnnotationProvider annotationProvider = vcs.getAnnotationProvider();
    if (annotationProvider == null) return false;
    final FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(file);
    if (fileStatus == FileStatus.UNKNOWN || fileStatus == FileStatus.ADDED) {
      return false;
    }
    return hasTextEditor(file);
  }

  private static boolean hasTextEditor(VirtualFile selectedFile) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType fileType = fileTypeManager.getFileTypeByFile(selectedFile);
    return !fileType.isBinary() && fileType != StdFileTypes.GUI_DESIGNER_FORM;
  }

  public boolean isSelected(AnActionEvent e) {
    VcsContext context = PeerFactory.getInstance().getVcsContextFactory().createContextOn(e);
    Editor editor = context.getEditor();
    if (editor == null) return false;
    Collection annotations = editor.getUserData(KEY_IN_EDITOR);
    return annotations != null && !annotations.isEmpty();
  }

  public void setSelected(AnActionEvent e, boolean state) {
    VcsContext context = PeerFactory.getInstance().getVcsContextFactory().createContextOn(e);
    Editor editor = context.getEditor();
    if (!state) {
      if (editor != null) {
        editor.getGutter().closeAllAnnotations();
      }
    }
    else {
      if (editor == null) {
        VirtualFile selectedFile = context.getSelectedFile();
        FileEditor[] fileEditors = FileEditorManager.getInstance(context.getProject()).openFile(selectedFile, false);
        for (FileEditor fileEditor : fileEditors) {
          if (fileEditor instanceof TextEditor) {
            editor = ((TextEditor)fileEditor).getEditor();
          }
        }
      }

      LOG.assertTrue(editor != null);

      doAnnotate(editor, context);

    }
  }

  private static void doAnnotate(final Editor editor, final VcsContext context) {
    final VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
    Project project = context.getProject();
    if (project == null) return;
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
    if (vcs == null) return;
    final AnnotationProvider annotationProvider = vcs.getAnnotationProvider();

    final Ref<FileAnnotation> fileAnnotationRef = new Ref<FileAnnotation>();
    final Ref<VcsException> exceptionRef = new Ref<VcsException>();
    boolean result = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        try {
          fileAnnotationRef.set(annotationProvider.annotate(file));
        }
        catch (VcsException e) {
          exceptionRef.set(e);
        }
      }
    }, VcsBundle.message("retrieving.annotations"), true, project);
    if (!result) {
      return;
    }
    if (!exceptionRef.isNull()) {
      AbstractVcsHelper.getInstance(project).showErrors(Arrays.asList(exceptionRef.get()), VcsBundle.message("message.title.annotate"));
    }
    if (fileAnnotationRef.isNull()) {
      return;
    }

    FileAnnotation fileAnnotation = fileAnnotationRef.get();
    String upToDateContent = fileAnnotation.getAnnotatedContent();

    final UpToDateLineNumberProvider getUpToDateLineNumber = LocalVcs.getInstance(project).getUpToDateLineNumberProvider(
      editor.getDocument(),
      upToDateContent);

    editor.getGutter().closeAllAnnotations();

    Collection<AnnotationFieldGutter> annotations = editor.getUserData(KEY_IN_EDITOR);
    if (annotations == null) {
      annotations = new HashSet<AnnotationFieldGutter>();
      editor.putUserData(KEY_IN_EDITOR, annotations);
    }

    final LineAnnotationAspect[] aspects = fileAnnotation.getAspects();
    for (LineAnnotationAspect aspect : aspects) {
      final AnnotationFieldGutter gutter = new AnnotationFieldGutter(getUpToDateLineNumber, fileAnnotation, editor, aspect);
      if (aspect instanceof EditorGutterAction) {
        editor.getGutter().registerTextAnnotation(gutter, gutter);
      }
      else {
        editor.getGutter().registerTextAnnotation(gutter);
      }
      annotations.add(gutter);
    }
  }

  private static class AnnotationFieldGutter implements TextAnnotationGutterProvider, EditorGutterAction {
    private final UpToDateLineNumberProvider myGetUpToDateLineNumber;
    private final FileAnnotation myAnnotation;
    private final Editor myEditor;
    private LineAnnotationAspect myAspect;
    private AnnotationListener myListener;

    public AnnotationFieldGutter(UpToDateLineNumberProvider getUpToDateLineNumber,
                                 FileAnnotation annotation,
                                 Editor editor,
                                 LineAnnotationAspect aspect) {
      myGetUpToDateLineNumber = getUpToDateLineNumber;
      myAnnotation = annotation;
      myEditor = editor;
      myAspect = aspect;

      myListener = new AnnotationListener() {
        public void onAnnotationChanged() {
          myEditor.getGutter().closeAllAnnotations();
        }
      };

      myAnnotation.addListener(myListener);
    }

    public String getLineText(int line, Editor editor) {
      int currentLine = myGetUpToDateLineNumber.getLineNumber(line);
      if (currentLine == UpToDateLineNumberProvider.ABSENT_LINE_NUMBER) return "";
      return myAspect.getValue(currentLine);
    }

    @Nullable
    public String getToolTip(final int line, final Editor editor) {
      return XmlStringUtil.escapeString(myAnnotation.getToolTip(line));
    }

    public void doAction(int line) {
      if (myAspect instanceof EditorGutterAction) {
        int currentLine = myGetUpToDateLineNumber.getLineNumber(line);
        if (currentLine == UpToDateLineNumberProvider.ABSENT_LINE_NUMBER) return;

        ((EditorGutterAction)myAspect).doAction(currentLine);
      }

    }

    public Cursor getCursor(final int line) {
      if (myAspect instanceof EditorGutterAction) {
        int currentLine = myGetUpToDateLineNumber.getLineNumber(line);
        if (currentLine == UpToDateLineNumberProvider.ABSENT_LINE_NUMBER) return Cursor.getDefaultCursor();

        return ((EditorGutterAction)myAspect).getCursor(currentLine);
      } else {
        return Cursor.getDefaultCursor();
      }

    }

    public void gutterClosed() {
      myAnnotation.removeListener(myListener);
      myAnnotation.dispose();
      myEditor.getUserData(KEY_IN_EDITOR).remove(this);
    }
  }
}
