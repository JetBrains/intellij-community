// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.breadcrumbs;

import com.intellij.codeInsight.daemon.impl.tagTreeHighlighting.XmlTagTreeHighlightingUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
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

    myBreadcrumbsCollector = findBreadcrumbsCollector();
    if (myFile != null) {
      myBreadcrumbsCollector.watchForChanges(myFile, this, () -> queueUpdate());
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

    queueUpdate();
  }

  private FileBreadcrumbsCollector findBreadcrumbsCollector() {
    if (myFile != null) {
      for (FileBreadcrumbsCollector extension : FileBreadcrumbsCollector.EP_NAME.getExtensions(myProject)) {
        if (extension.handlesFile(myFile)) {
          return extension;
        }
      }
    }
    return new PsiFileBreadcrumbsCollector(myProject);
  }

  private void updateCrumbs() {
    if (myEditor == null || myFile == null || myEditor.isDisposed()) return;

    if (myAsyncUpdateProgress != null) {
      myAsyncUpdateProgress.cancel();
    }

    ProgressIndicator progress = new ProgressIndicatorBase();
    myAsyncUpdateProgress = progress;

    myBreadcrumbsCollector.updateCrumbs(myFile, myEditor, myAsyncUpdateProgress, (crumbs) -> {
      if (!progress.isCanceled() && myEditor != null && !myEditor.isDisposed() && !myProject.isDisposed()) {
        breadcrumbs.setFont(getNewFont(myEditor));
        if (!breadcrumbs.isShowing()) {
          breadcrumbs.setCrumbs(null);
          return;
        }
        breadcrumbs.setCrumbs(crumbs);
      }
    });
  }

  public void queueUpdate() {
    myQueue.cancelAllUpdates();
    myQueue.queue(myUpdate);
  }

  @Deprecated
  public JComponent getComponent() {
    return this;
  }

  private void itemSelected(Crumb crumb, InputEvent event) {
    if (event == null || !(crumb instanceof NavigatableCrumb)) return;
    NavigatableCrumb navigatableCrumb = (NavigatableCrumb) crumb;
    myUserCaretChange = false;
    navigatableCrumb.navigate(myEditor, event.isShiftDown() || event.isMetaDown());
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
      final TextRange range = ((NavigatableCrumb) crumb).getHighlightRange();
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
    breadcrumbs.setCrumbs(null);
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
