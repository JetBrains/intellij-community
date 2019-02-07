// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.breadcrumbs;

import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector;
import com.intellij.codeInsight.daemon.impl.tagTreeHighlighting.XmlTagTreeHighlightingUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.Gray;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.MouseEventAdapter;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.ui.RelativeFont.SMALL;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.ui.UIUtil.getLabelFont;

/**
 * @author spleaner
 */
public class BreadcrumbsXmlWrapper extends JComponent implements Disposable {
  final PsiBreadcrumbs breadcrumbs = new PsiBreadcrumbs();

  private final Project myProject;
  private Editor myEditor;
  private Collection<RangeHighlighter> myHighlighed;
  private final VirtualFile myFile;
  private boolean myUserCaretChange = true;
  private final MergingUpdateQueue myQueue = new MergingUpdateQueue("Breadcrumbs.Queue", 200, true, breadcrumbs);

  private List<BreadcrumbListener> myBreadcrumbListeners = new ArrayList<>();

  private final Update myUpdate = new Update(this) {
    @Override
    public void run() {
      updateCrumbs();
    }

    @Override
    public boolean canEat(final Update update) {
      return true;
    }
  };

  private ProgressIndicator myAsyncUpdateProgress = null;
  private final FileBreadcrumbsCollector myBreadcrumbsCollector;

  public static final Key<BreadcrumbsXmlWrapper> BREADCRUMBS_COMPONENT_KEY = new Key<>("BREADCRUMBS_KEY");
  private static final Iterable<? extends Crumb> EMPTY_BREADCRUMBS = ContainerUtil.emptyIterable();

  public BreadcrumbsXmlWrapper(@NotNull final Editor editor) {
    myEditor = editor;
    myEditor.putUserData(BREADCRUMBS_COMPONENT_KEY, this);
    if (editor instanceof EditorEx) {
      ((EditorEx)editor).addPropertyChangeListener(this::updateEditorFont, this);
    }

    final Project project = editor.getProject();
    assert project != null;
    myProject = project;

    myFile = FileDocumentManager.getInstance().getFile(myEditor.getDocument());

    final FileStatusManager manager = FileStatusManager.getInstance(project);
    manager.addFileStatusListener(new FileStatusListener() {
      @Override
      public void fileStatusesChanged() {
        queueUpdate();
      }
    }, this);

    final CaretListener caretListener = new CaretListener() {
      @Override
      public void caretPositionChanged(@NotNull final CaretEvent e) {
        if (myUserCaretChange) {
          queueUpdate();
        }

        myUserCaretChange = true;
      }
    };

    editor.getCaretModel().addCaretListener(caretListener, this);

    myBreadcrumbsCollector = FileBreadcrumbsCollector.findBreadcrumbsCollector(myProject, myFile);
    if (myFile != null) {
      myBreadcrumbsCollector.watchForChanges(myFile, editor, this, () -> queueUpdate());
    }

    breadcrumbs.onHover(this::itemHovered);
    breadcrumbs.onSelect(this::itemSelected);
    breadcrumbs.setFont(getNewFont(myEditor));

    JScrollPane pane = createScrollPane(breadcrumbs, true);
    pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
    pane.getHorizontalScrollBar().setEnabled(false);
    setLayout(new BorderLayout());
    add(BorderLayout.CENTER, pane);

    EditorGutter gutter = editor.getGutter();
    if (gutter instanceof EditorGutterComponentEx) {
      EditorGutterComponentEx gutterComponent = (EditorGutterComponentEx)gutter;
      MouseEventAdapter mouseListener = new MouseEventAdapter<EditorGutterComponentEx>(gutterComponent) {
        @NotNull
        @Override
        protected MouseEvent convert(@NotNull MouseEvent event) {
          return convert(event, gutterComponent);
        }
      };
      ComponentAdapter resizeListener = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent event) {
          breadcrumbs.updateBorder(gutterComponent.getWhitespaceSeparatorOffset());
          breadcrumbs.setFont(getNewFont(myEditor));
        }
      };

      addComponentListener(resizeListener);
      gutterComponent.addComponentListener(resizeListener);
      breadcrumbs.addMouseListener(mouseListener);
      Disposer.register(this, () -> {
        removeComponentListener(resizeListener);
        gutterComponent.removeComponentListener(resizeListener);
        breadcrumbs.removeMouseListener(mouseListener);
      });
      breadcrumbs.updateBorder(gutterComponent.getWhitespaceSeparatorOffset());
    }
    else {
      breadcrumbs.updateBorder(0);
    }
    Disposer.register(this, new UiNotifyConnector(breadcrumbs, myQueue));
    Disposer.register(this, myQueue);

    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      myQueue.setPassThrough(true);
    }

    queueUpdate();
  }

  private void updateCrumbs() {
    if (myEditor == null || myFile == null || myEditor.isDisposed()) return;

    if (myAsyncUpdateProgress != null) {
      myAsyncUpdateProgress.cancel();
    }

    ProgressIndicator progress = new ProgressIndicatorBase();
    myAsyncUpdateProgress = progress;

    myBreadcrumbsCollector.updateCrumbs(myFile, myEditor, myEditor.getCaretModel().getOffset(), myAsyncUpdateProgress, (crumbs) -> {
      if (!progress.isCanceled() && myEditor != null && !myEditor.isDisposed() && !myProject.isDisposed()) {
        if (!breadcrumbs.isShowing() && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
          crumbs = EMPTY_BREADCRUMBS;
        }
        breadcrumbs.setFont(getNewFont(myEditor));
        breadcrumbs.setCrumbs(crumbs);
        notifyListeners(crumbs);
      }
    });
  }

  public void queueUpdate() {
    myQueue.cancelAllUpdates();
    myQueue.queue(myUpdate);
  }

  public void addBreadcrumbListener(BreadcrumbListener listener, Disposable parentDisposable) {
    myBreadcrumbListeners.add(listener);
    Disposer.register(parentDisposable, () -> myBreadcrumbListeners.remove(listener));
  }

  public void removeBreadcrumbListener(BreadcrumbListener listener) {
    myBreadcrumbListeners.remove(listener);
  }

  private void notifyListeners(@NotNull Iterable<? extends Crumb> breadcrumbs) {
    for (BreadcrumbListener listener : myBreadcrumbListeners) {
      listener.breadcrumbsChanged(breadcrumbs);
    }
  }

  @Deprecated
  public JComponent getComponent() {
    return this;
  }

  private void itemSelected(Crumb crumb, InputEvent event) {
    if (event == null || !(crumb instanceof NavigatableCrumb)) return;
    NavigatableCrumb navigatableCrumb = (NavigatableCrumb)crumb;
    navigate(navigatableCrumb, event.isShiftDown() || event.isMetaDown());
  }

  public void navigate(NavigatableCrumb crumb, boolean withSelection) {
    myUserCaretChange = false;
    crumb.navigate(myEditor, withSelection);
  }

  private void itemHovered(Crumb crumb, @SuppressWarnings("unused") InputEvent event) {
    if (!Registry.is("editor.breadcrumbs.highlight.on.hover")) {
      return;
    }

    HighlightManager hm = HighlightManager.getInstance(myProject);
    if (myHighlighed != null) {
      for (RangeHighlighter highlighter : myHighlighed) {
        hm.removeSegmentHighlighter(myEditor, highlighter);
      }
      myHighlighed = null;
    }
    if (crumb instanceof NavigatableCrumb) {
      final TextRange range = ((NavigatableCrumb)crumb).getHighlightRange();
      if (range == null) return;
      final TextAttributes attributes = new TextAttributes();
      final CrumbPresentation p = PsiCrumb.getPresentation(crumb);
      Color color = p == null ? null : p.getBackgroundColor(false, false, false);
      if (color == null) color = BreadcrumbsComponent.ButtonSettings.getBackgroundColor(false, false, false, false);
      if (color == null) color = UIUtil.getLabelBackground();
      final Color background = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.CARET_ROW_COLOR);
      attributes.setBackgroundColor(XmlTagTreeHighlightingUtil.makeTransparent(color, background != null ? background : Gray._200, 0.3));
      myHighlighed = new ArrayList<>(1);
      int flags = HighlightManager.HIDE_BY_ESCAPE | HighlightManager.HIDE_BY_TEXT_CHANGE | HighlightManager.HIDE_BY_ANY_KEY;
      hm.addOccurrenceHighlight(myEditor, range.getStartOffset(), range.getEndOffset(), attributes, flags, myHighlighed, null);
    }
  }

  @Nullable
  public static BreadcrumbsXmlWrapper getBreadcrumbsComponent(@NotNull Editor editor) {
    return editor.getUserData(BREADCRUMBS_COMPONENT_KEY);
  }

  @Override
  public void dispose() {
    if (myEditor != null) {
      myEditor.putUserData(BREADCRUMBS_COMPONENT_KEY, null);
    }
    myEditor = null;
    breadcrumbs.setCrumbs(EMPTY_BREADCRUMBS);
    notifyListeners(EMPTY_BREADCRUMBS);
  }

  private void updateEditorFont(PropertyChangeEvent event) {
    if (EditorEx.PROP_FONT_SIZE.equals(event.getPropertyName())) queueUpdate();
  }

  private static Font getNewFont(Editor editor) {
    Font font = editor == null || Registry.is("editor.breadcrumbs.system.font") ? getLabelFont() : getEditorFont(editor);
    return UISettings.getInstance().getUseSmallLabelsOnTabs() ? SMALL.derive(font) : font;
  }

  private static Font getEditorFont(Editor editor) {
    return ComplementaryFontsRegistry.getFontAbleToDisplay('a', Font.PLAIN, editor.getColorsScheme().getFontPreferences(),
                                                           null).getFont();
  }
}
