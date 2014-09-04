package org.jetbrains.plugins.coursecreator.highlighting;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.coursecreator.CCProjectService;
import org.jetbrains.plugins.coursecreator.format.*;

import java.util.Collection;
import java.util.List;

public class CCTaskLineMarkerProvider implements LineMarkerProvider {
  private static final Logger LOG = Logger.getInstance(CCTaskLineMarkerProvider.class.getName());

  @Nullable
  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
    return null;
  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull final Collection<LineMarkerInfo> result) {
    for (PsiElement element : elements) {
      if (element instanceof PsiFile) {
        final Project project = element.getProject();
        final Course course = CCProjectService.getInstance(project).getCourse();
        if (course == null) return;
        final String taskFileName = ((PsiFile) element).getName();
        final PsiDirectory taskDir = ((PsiFile) element).getParent();
        if (taskDir == null) continue;
        final String taskDirName = taskDir.getName();
        final PsiDirectory lessonDir = taskDir.getParentDirectory();
        if (lessonDir == null) continue;
        final String lessonDirName = lessonDir.getName();
        final Lesson lesson = course.getLesson(lessonDirName);
        if (lesson == null) continue;
        final Task task = lesson.getTask(taskDirName);
        final TaskFile taskFile = task.getTaskFile(taskFileName);
        if (taskFile == null) continue;
        final Document document = PsiDocumentManager.getInstance(project).getDocument((PsiFile) element);
        if (document == null) continue;
        for (final TaskWindow taskWindow : taskFile.getTaskWindows()) {
          if (taskWindow.line > document.getLineCount()) continue;
          final int lineStartOffset = document.getLineStartOffset(taskWindow.line);
          final int offset = lineStartOffset + taskWindow.start;
          if (offset > document.getTextLength()) continue;
          final TextRange textRange = TextRange.create(offset, offset + taskWindow.getReplacementLength());
          @SuppressWarnings("unchecked")
          final LineMarkerInfo info = new LineMarkerInfo(element, textRange,
              IconLoader.getIcon("/icons/gutter.png"), Pass.UPDATE_OVERRIDEN_MARKERS,
              null, null, GutterIconRenderer.Alignment.CENTER) {
            @Nullable
            @Override
            public GutterIconRenderer createGutterRenderer() {
              return new TaskTextGutter(taskWindow, this);
            }
          };
          result.add(info);
        }
      }
    }
  }
}
