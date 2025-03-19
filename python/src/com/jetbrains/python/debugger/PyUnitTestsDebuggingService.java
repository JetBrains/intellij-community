// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XSourcePosition;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.List;
import java.util.*;

@Service(Service.Level.PROJECT)
final class PyUnitTestsDebuggingService {
  private static final @NotNull Map<XDebugSession, List<Inlay<FailedTestInlayRenderer>>> ourActiveInlays = new WeakHashMap<>();
  private static final @NotNull Map<Inlay<?>, ComponentListener> ourEditorListeners = Maps.newHashMap();

  private static final @NotNull Set<String> PYTEST_SET_UP_AND_TEAR_DOWN_FUNCTION_NAMES = ImmutableSet.of(
    "call_fixture_func", "_eval_scope_callable", "_teardown_yield_fixture");
  private static final @NotNull Set<String> UNITTEST_SET_UP_AND_TEAR_DOWN_FUNCTION_NAMES = ImmutableSet.of("_callSetUp", "_callTearDown");
  private static final @NotNull Map<String, Set<String>> SET_UP_AND_TEAR_DOWN_FUNCTIONS_BY_FRAMEWORK = ImmutableMap.of(
    "pytest", PYTEST_SET_UP_AND_TEAR_DOWN_FUNCTION_NAMES, "unittest", UNITTEST_SET_UP_AND_TEAR_DOWN_FUNCTION_NAMES);

  /**
   * Show an inlay with the fail information at the place the test has failed.
   *
   * @param debugSession session from which process the service was called from
   * @param frame frame of the test function where an error has occurred
   * @param exceptionType the type name of an exception that made test fail
   * @param errorMessage  the message describing the reason of fail
   * @param isTestSetUpFail if the error happened in a test set up function or method
   */
  public void showFailedTestInlay(@NotNull XDebugSession debugSession, @NotNull PyStackFrame frame,
                                  @NotNull String exceptionType, @NotNull String errorMessage, boolean isTestSetUpFail) {
    AppUIUtil.invokeLaterIfProjectAlive(debugSession.getProject(), () -> {
      XSourcePosition position = frame.getPosition();
      if (position == null) return;

      Consumer<Editor> addInlayToEditor = makeAddInlayToEditorFunction(debugSession, frame, exceptionType, errorMessage, isTestSetUpFail);
      Arrays.stream(getAllEditorsForFile(debugSession.getProject(), position.getFile())).forEach(
        editor -> addInlayToEditor.consume(editor));
    });
  }

  private Consumer<Editor> makeAddInlayToEditorFunction(@NotNull XDebugSession debugSession, @NotNull PyStackFrame frame,
                                                        @NotNull String exceptionType, @NotNull String errorMessage,
                                                        boolean isTestSetUpFail) {
    return editor -> {
      InlayModel inlayModel = editor.getInlayModel();

      Inlay<FailedTestInlayRenderer> inlay = inlayModel.addBlockElement(
        frame.getPosition().getOffset(), true, false, 0,
        new FailedTestInlayRenderer(exceptionType, errorMessage, isTestSetUpFail));

      if (inlay == null) return;

      ourActiveInlays.putIfAbsent(debugSession, new ArrayList<>());
      ourActiveInlays.get(debugSession).add(inlay);

      ComponentListener listener = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          Rectangle inlayBounds = inlay.getBounds();
          if (inlayBounds != null && e.getComponent().getWidth() != inlay.getBounds().width) {
            inlay.update();
          }
        }
      };
      editor.getComponent().addComponentListener(listener);
      ourEditorListeners.put(inlay, listener);

      if (isTestSetUpFail) {
        // Errors in test set up should be deleted after the session resumes since set up is called
        // multiple times and we don't want to stack up the inlays below the erroneous line.
        debugSession.addSessionListener(new XDebugSessionListener() {
          @Override
          public void sessionResumed() {
            Disposer.dispose(inlay);
            debugSession.removeSessionListener(this);
          }
        });
      }
    };
  }

  public static void removeInlaysAssociatedWithSession(XDebugSession session) {
    List<Inlay<FailedTestInlayRenderer>> inlays = ourActiveInlays.getOrDefault(session, null);
    if (inlays != null) {
      inlays.forEach((inlay) -> {
        inlay.getEditor().getComponent().removeComponentListener(ourEditorListeners.get(inlay));
        ApplicationManager.getApplication().invokeLater(() -> Disposer.dispose(inlay));
      });
      ourActiveInlays.remove(session);
    }
  }

  /**
   * Check if the error has happened while set up or tear down.
   *
   * @param frames of the thread where an exception has occured
   */
  public static boolean isErrorInTestSetUpOrTearDown(@NotNull List<PyStackFrameInfo> frames) {
    for (PyStackFrameInfo frameInfo : frames) {
      if (ContainerUtil.exists(SET_UP_AND_TEAR_DOWN_FUNCTIONS_BY_FRAMEWORK.values(), names -> names.contains(frameInfo.getName())))
        return true;
    }
    return false;
  }

  private static final class FailedTestInlayRenderer implements EditorCustomElementRenderer {

    private static final float HEIGHT_FACTOR = .5f;
    private static final short RIGHT_BAR_THICKNESS = 2;
    private static final short INLAY_TEXT_INDENT = 10;

    private final @NotNull String myExceptionType;
    private final @NotNull String myErrorMessage;
    private final boolean myIsTestSetUpFail;

    private int myCurrentLineNumber = 1;

    private FailedTestInlayRenderer(@NotNull String exceptionType, @NotNull String errorMessage, boolean isTestSetUpFail) {
      myExceptionType = exceptionType;
      myErrorMessage = StringUtil.trimTrailing(errorMessage);
      myIsTestSetUpFail = isTestSetUpFail;
    }

    @Override
    public int calcWidthInPixels(@NotNull Inlay inlay) {
      return inlay.getEditor().getComponent().getWidth();
    }

    @Override
    public int calcHeightInPixels(@NotNull Inlay inlay) {
      int lineHeight = inlay.getEditor().getLineHeight();
      return lineHeight + lineHeight * (StringUtil.countNewLines(myErrorMessage) + 1) + Math.round(lineHeight * HEIGHT_FACTOR);
    }

    @Override
    public @NotNull GutterIconRenderer calcGutterIconRenderer(@NotNull Inlay inlay) {
      return new PyUnitTestsDebuggingService.FailedTestGutterIconRenderer();
    }

    @Override
    public void paint(@NotNull Inlay inlay,
                      @NotNull Graphics g,
                      @NotNull Rectangle targetRegion,
                      @NotNull TextAttributes textAttributes) {
      resetCurrentLineNumber();

      g.setColor(getInlayBackgroundColor());
      g.fillRect(targetRegion.x, targetRegion.y, targetRegion.width, targetRegion.height);

      g.setColor(NamedColorUtil.getErrorForeground());
      g.fillRect(targetRegion.x, targetRegion.y, targetRegion.x + RIGHT_BAR_THICKNESS, calcHeightInPixels(inlay));

      g.setFont(getFont(inlay.getEditor()));

      g.setColor(NamedColorUtil.getErrorForeground());
      drawStringToInlayBox((myIsTestSetUpFail ? getErrorInTestSetUpCaption() : getFailedTestCaption()) + ":", inlay, g, targetRegion);

      g.setColor(UIUtil.getToolTipForeground());

      String[] lines = StringUtil.splitByLines(myErrorMessage);
      drawStringToInlayBox(myExceptionType + ": " + lines[0], inlay, g, targetRegion);

      if (lines.length > 1) {
        for (int k = 1; k < lines.length; k++) {
          drawStringToInlayBox(lines[k], inlay, g, targetRegion);
        }
      }
    }

    private static @NotNull Font getFont(@NotNull Editor editor) {
      String fontName = UIUtil.getToolTipFont().getFontName();
      int fontSize = editor.getColorsScheme().getEditorFontSize();
      return UIUtil.getFontWithFallback(fontName, Font.PLAIN, fontSize);
    }

    private void drawStringToInlayBox(@NotNull String str, @NotNull Inlay<?> inlay, @NotNull Graphics g, @NotNull Rectangle targetRegion) {
      g.drawString(str, targetRegion.x + INLAY_TEXT_INDENT, targetRegion.y + inlay.getEditor().getLineHeight() * myCurrentLineNumber);
      myCurrentLineNumber++;
    }

    private void resetCurrentLineNumber() {
      myCurrentLineNumber = 1;
    }
  }

  private static Editor @NotNull [] getAllEditorsForFile(@NotNull Project project, @NotNull VirtualFile file) {
    FileEditor[] fileEditors = FileEditorManager.getInstance(project).getAllEditors(file);

    if (fileEditors.length == 0) {
      Editor editor = openEditorForFile(project, file);
      return editor == null ? Editor.EMPTY_ARRAY : new Editor[]{openEditorForFile(project, file)};
    }

    return Arrays.stream(fileEditors)
      .map(fileEditor -> (TextEditor)fileEditor)
      .map(textEditor -> textEditor.getEditor())
      .toArray(Editor[]::new);
  }

  private static @Nullable Editor openEditorForFile(@NotNull Project project, @NotNull VirtualFile file) {
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, 0);
    return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

  private static @NotNull Color getInlayBackgroundColor() {
    return JBColor.isBright() ? new JBColor(Gray._240, Gray._192) : JBColor.WHITE;
  }

  private static @Nls String getFailedTestCaption() {
    return PyBundle.messagePointer("debugger.test.failed.caption").get();
  }

  private static @Nls String getErrorInTestSetUpCaption() {
    return PyBundle.messagePointer("debugger.error.in.test.setup.or.teardown.caption").get();
  }

  private static final class FailedTestGutterIconRenderer extends GutterIconRenderer {
    private static final FailedTestGutterIconRenderer INSTANCE = new FailedTestGutterIconRenderer();

    private FailedTestGutterIconRenderer() {}

    public static PyUnitTestsDebuggingService.FailedTestGutterIconRenderer getInstance() {
      return INSTANCE;
    }

    @Override
    public @NotNull Icon getIcon() {
      return AllIcons.Debugger.Db_exception_breakpoint;
    }

    @Override
    public @NotNull Alignment getAlignment() {
      return Alignment.RIGHT;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof FailedTestGutterIconRenderer;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }
}
