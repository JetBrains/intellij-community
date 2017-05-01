/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xml.breadcrumbs;

import com.intellij.codeInsight.daemon.impl.tagTreeHighlighting.XmlTagTreeHighlightingUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.CaretAdapter;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.Gray;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.MouseEventAdapter;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
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
import java.util.LinkedList;
import java.util.PriorityQueue;

import static com.intellij.ui.ScrollPaneFactory.createScrollPane;

/**
 * @author spleaner
 */
public class BreadcrumbsXmlWrapper extends JComponent implements Disposable {
  final PsiBreadcrumbs breadcrumbs = new PsiBreadcrumbs();

  private final static Logger LOG = Logger.getInstance(BreadcrumbsXmlWrapper.class);

  private final Project myProject;
  private Editor myEditor;
  private Collection<RangeHighlighter> myHighlighed;
  private final VirtualFile myFile;
  private boolean myUserCaretChange = true;
  private final MergingUpdateQueue myQueue = new MergingUpdateQueue("Breadcrumbs.Queue", 200, true, breadcrumbs);
  private final BreadcrumbsProvider myInfoProvider;
  private final Update myUpdate = new MyUpdate(this);

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

    myFile = getVirtualFile(myEditor);

    final FileStatusManager manager = FileStatusManager.getInstance(project);
    manager.addFileStatusListener(new FileStatusListener() {
      @Override
      public void fileStatusesChanged() {
        updateCrumbs();
      }
    }, this);

    myInfoProvider = findInfoProvider(myFile, myProject);

    final CaretListener caretListener = new CaretAdapter() {
      @Override
      public void caretPositionChanged(final CaretEvent e) {
        if (myUserCaretChange) {
          queueUpdate();
        }

        myUserCaretChange = true;
      }
    };

    editor.getCaretModel().addCaretListener(caretListener);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        editor.getCaretModel().removeCaretListener(caretListener);
      }
    });

    PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
      @Override
      public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
        PsiFile psiFile = event.getFile();
        VirtualFile file = psiFile == null ? null : psiFile.getVirtualFile();
        if (!Comparing.equal(file, myFile)) return;
        queueUpdate();
      }

      @Override
      public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
        propertyChanged(event);
      }

      @Override
      public void childMoved(@NotNull PsiTreeChangeEvent event) {
        propertyChanged(event);
      }

      @Override
      public void childReplaced(@NotNull PsiTreeChangeEvent event) {
        propertyChanged(event);
      }

      @Override
      public void childRemoved(@NotNull PsiTreeChangeEvent event) {
        propertyChanged(event);
      }

      @Override
      public void childAdded(@NotNull PsiTreeChangeEvent event) {
        propertyChanged(event);
      }
    }, this);

    breadcrumbs.onHover(this::itemHovered);
    breadcrumbs.onSelect(this::itemSelected);
    breadcrumbs.setFont(getEditorFont(myEditor));

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
          breadcrumbs.setFont(getEditorFont(myEditor));
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

  private void updateCrumbs() {
    if (breadcrumbs != null && myEditor != null && !myEditor.isDisposed()) {
      breadcrumbs.setFont(getEditorFont(myEditor));
      updateCrumbs(myEditor.getCaretModel().getLogicalPosition());
    }
  }

  public void queueUpdate() {
    myQueue.cancelAllUpdates();
    myQueue.queue(myUpdate);
  }

  private void moveEditorCaretTo(@NotNull final PsiElement element) {
    if (element.isValid()) {
      setUserCaretChange(false);
      myEditor.getCaretModel().moveToOffset(element.getTextOffset());
      myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }
  }

  @Nullable
  private static BreadcrumbsProvider findProviderForElement(@NotNull PsiElement element, BreadcrumbsProvider defaultProvider) {
    final BreadcrumbsProvider provider = getInfoProvider(element.getLanguage());
    return provider == null ? defaultProvider : provider;
  }

  private static PsiElement[] toPsiElementArray(Collection<PsiCrumb> items) {
    final PsiElement[] elements = new PsiElement[items.size()];
    int index = 0;
    for (PsiCrumb item : items) {
      elements[index++] = item.element;
    }
    return elements;
  }

  @Nullable
  private static CrumbPresentation[] getCrumbPresentations(final PsiElement[] elements) {
    for (BreadcrumbsPresentationProvider provider : BreadcrumbsPresentationProvider.EP_NAME.getExtensions()) {
      final CrumbPresentation[] presentations = provider.getCrumbPresentations(elements);
      if (presentations != null) {
        return presentations;
      }
    }
    return null;
  }

  private void setUserCaretChange(final boolean userCaretChange) {
    myUserCaretChange = userCaretChange;
  }

  @Nullable
  private static Iterable<PsiCrumb> getPresentableLineElements(@NotNull final LogicalPosition position,
                                                               final VirtualFile file,
                                                               final Editor editor,
                                                               final Project project,
                                                               final BreadcrumbsProvider defaultInfoProvider) {
    final LinkedList<PsiCrumb> result =
      getLineElements(editor.logicalPositionToOffset(position), file, project, defaultInfoProvider);

    if (result == null) return null;

    final PsiElement[] elements = toPsiElementArray(result);
    final CrumbPresentation[] presentations = getCrumbPresentations(elements);
    if (presentations != null) {
      int i = 0;
      for (PsiCrumb item : result) {
        item.presentation = presentations[i++];
      }
    }

    return result;
  }

  @Nullable
  public static PsiElement[] getLinePsiElements(int offset, VirtualFile file, Project project, BreadcrumbsProvider infoProvider) {
    final LinkedList<PsiCrumb> lineElements = getLineElements(offset, file, project, infoProvider);
    return lineElements != null ? toPsiElementArray(lineElements) : null;
  }

  @Nullable
  private static LinkedList<PsiCrumb> getLineElements(final int offset,
                                                      VirtualFile file,
                                                      Project project,
                                                      BreadcrumbsProvider defaultInfoProvider) {
    PsiElement element = findFirstBreadcrumbedElement(offset, file, project, defaultInfoProvider);
    if (element == null) return null;

    final LinkedList<PsiCrumb> result = new LinkedList<>();
    while (element != null) {
      BreadcrumbsProvider provider = findProviderForElement(element, defaultInfoProvider);

      if (provider != null && provider.acceptElement(element)) {
        result.addFirst(new PsiCrumb(element, provider));
      }

      element = getParent(element, provider);
    }
    return result;
  }

  @Nullable
  private static PsiElement findFirstBreadcrumbedElement(final int offset,
                                                         final VirtualFile file,
                                                         final Project project,
                                                         final BreadcrumbsProvider defaultInfoProvider) {
    if (file == null || !file.isValid()) return null;

    PriorityQueue<PsiElement> leafs =
      new PriorityQueue<>(3, (o1, o2) -> {
        TextRange range1 = o1.getTextRange();
        if (range1 == null) {
          LOG.error(o1 + " returned null range");
          return 1;
        }
        TextRange range2 = o2.getTextRange();
        if (range2 == null) {
          LOG.error(o2 + " returned null range");
          return -1;
        }
        return range2.getStartOffset() - range1.getStartOffset();
      });
    FileViewProvider viewProvider = findViewProvider(file, project);
    if (viewProvider == null) return null;

    for (final Language language : viewProvider.getLanguages()) {
      ContainerUtil.addIfNotNull(leafs, viewProvider.findElementAt(offset, language));
    }
    while (!leafs.isEmpty()) {
      final PsiElement element = leafs.remove();
      if (!element.isValid()) continue;

      BreadcrumbsProvider provider = findProviderForElement(element, defaultInfoProvider);
      if (provider != null && provider.acceptElement(element)) {
        return element;
      }
      if (!(element instanceof PsiFile)) {
        ContainerUtil.addIfNotNull(leafs, getParent(element, provider));
      }
    }
    return null;
  }

  @Nullable
  private static PsiElement getParent(@NotNull PsiElement element, @Nullable BreadcrumbsProvider provider) {
    return provider != null ? provider.getParent(element) : element.getParent();
  }

  @Nullable
  private static FileViewProvider findViewProvider(final VirtualFile file, final Project project) {
    if (file == null) return null;
    return PsiManager.getInstance(project).findViewProvider(file);
  }

  @Nullable
  static FileViewProvider findViewProvider(Editor editor) {
    if (editor == null) return null;

    Project project = editor.getProject();
    if (project == null) return null;

    VirtualFile file = getVirtualFile(editor);
    return findViewProvider(file, project);
  }

  @Nullable
  static BreadcrumbsProvider findInfoProvider(VirtualFile file, Project project) {
    return project == null ? null : findInfoProvider(findViewProvider(file, project));
  }

  private static VirtualFile getVirtualFile(@NotNull Editor editor) {
    return FileDocumentManager.getInstance().getFile(editor.getDocument());
  }

  private void updateCrumbs(final LogicalPosition position) {
    if (myFile != null && myEditor != null && !myEditor.isDisposed() && !myProject.isDisposed()) {
      if (PsiDocumentManager.getInstance(myProject).isUncommited(myEditor.getDocument())) {
        return;
      }
      breadcrumbs.setCrumbs(getPresentableLineElements(position, myFile, myEditor, myProject, myInfoProvider));
    }
  }

  @Nullable
  public static BreadcrumbsProvider findInfoProvider(@Nullable FileViewProvider viewProvider) {
    if (viewProvider == null) return null;

    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    if (!settings.isBreadcrumbsShown()) return null;

    Language baseLang = viewProvider.getBaseLanguage();
    if (!settings.isBreadcrumbsShownFor(baseLang.getID())) return null;

    BreadcrumbsProvider provider = getInfoProvider(baseLang);
    if (provider == null) {
      for (Language language : viewProvider.getLanguages()) {
        if (settings.isBreadcrumbsShownFor(language.getID())) {
          provider = getInfoProvider(language);
          if (provider != null) break;
        }
      }
    }
    return provider;
  }

  @Deprecated
  public JComponent getComponent() {
    return this;
  }

  private void itemSelected(Crumb crumb, InputEvent event) {
    if (event == null) return;

    PsiElement psiElement = PsiCrumb.getElement(crumb);
    if (psiElement == null) return;

    moveEditorCaretTo(psiElement);

    if (event.isShiftDown() || event.isMetaDown()) {
      final TextRange range = psiElement.getTextRange();
      myEditor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
    }
  }

  private void itemHovered(Crumb crumb, InputEvent event) {
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
    PsiElement psiElement = PsiCrumb.getElement(crumb);
    if (psiElement != null) {
      final TextRange range = psiElement.getTextRange();
      final TextAttributes attributes = new TextAttributes();
      final CrumbPresentation p = PsiCrumb.getPresentation(crumb);
      final Color color = p != null
                          ? p.getBackgroundColor(false, false, false)
                          : BreadcrumbsComponent.ButtonSettings.getBackgroundColor(false, false, false, false);
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

  @Nullable
  private static BreadcrumbsProvider getInfoProvider(@NotNull Language language) {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    for (BreadcrumbsProvider provider : BreadcrumbsProvider.EP_NAME.getExtensions()) {
      for (Language supported : provider.getLanguages()) {
        if (settings.isBreadcrumbsShownFor(language.getID()) && supported.isKindOf(language)) {
          return provider;
        }
      }
    }
    return null;
  }

  private static class MyUpdate extends Update {
    private final BreadcrumbsXmlWrapper myBreadcrumbsComponent;

    public MyUpdate(@NonNls final BreadcrumbsXmlWrapper c) {
      super(c);

      myBreadcrumbsComponent = c;
    }

    @Override
    public void run() {
      myBreadcrumbsComponent.updateCrumbs();
    }

    @Override
    public boolean canEat(final Update update) {
      return true;
    }
  }

  private void updateEditorFont(PropertyChangeEvent event) {
    if (EditorEx.PROP_FONT_SIZE.equals(event.getPropertyName())) queueUpdate();
  }

  private static Font getEditorFont(Editor editor) {
    return ComplementaryFontsRegistry.getFontAbleToDisplay('a', Font.PLAIN, editor.getColorsScheme().getFontPreferences(),
                                                           null).getFont();
  }
}
