/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.CaretAdapter;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
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
import com.intellij.util.BitUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
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
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.PriorityQueue;

/**
 * @author spleaner
 */
public class BreadcrumbsXmlWrapper implements BreadcrumbsItemListener<BreadcrumbsPsiItem>, Disposable {
  private final BreadcrumbsComponent<BreadcrumbsPsiItem> myComponent;
  private final Project myProject;
  private Editor myEditor;
  private Collection<RangeHighlighter> myHighlighed;
  private final VirtualFile myFile;
  private boolean myUserCaretChange = true;
  private final MergingUpdateQueue myQueue;
  private final BreadcrumbsInfoProvider myInfoProvider;
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

    Document document = myEditor.getDocument();
    myFile = FileDocumentManager.getInstance().getFile(document);


    final FileStatusManager manager = FileStatusManager.getInstance(project);
    manager.addFileStatusListener(new FileStatusListener() {
      @Override
      public void fileStatusesChanged() {
        updateCrumbs();
      }

      @Override
      public void fileStatusChanged(@NotNull final VirtualFile virtualFile) {
      }
    }, this);

    UISettings.getInstance().addUISettingsListener(new UISettingsListener() {
      @Override
      public void uiSettingsChanged(UISettings source) {
        queueUpdate();
      }
    }, this);


    myInfoProvider = findInfoProvider(findViewProvider(myFile, myProject));

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
    },this);

    myComponent = new BreadcrumbsComponent<>();
    myComponent.addBreadcrumbsItemListener(this);
    myComponent.setFont(getEditorFont(myEditor));

    final EditorGutterComponentEx gutterComponent = ((EditorImpl)editor).getGutterComponentEx();
    final ComponentAdapter resizeListener = new ComponentAdapter() {
      @Override
      public void componentResized(final ComponentEvent e) {
        myComponent.setOffset(gutterComponent.getWhitespaceSeparatorOffset());
        queueUpdate();
      }
    };

    myComponent.addComponentListener(resizeListener);
    gutterComponent.addComponentListener(resizeListener);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        myComponent.removeComponentListener(resizeListener);
        gutterComponent.removeComponentListener(resizeListener);
      }
    });

    myQueue = new MergingUpdateQueue("Breadcrumbs.Queue", 200, true, myComponent);

    Disposer.register(this, new UiNotifyConnector(myComponent, myQueue));
    Disposer.register(this, myQueue);

    myComponent.setBorder(new JBEmptyBorder(JBUI.insets(2, 0, 1, 2)));
    queueUpdate();
  }

  private void updateCrumbs() {
    if (myComponent != null && myEditor != null && !myEditor.isDisposed()) {
      myComponent.setFont(getEditorFont(myEditor));
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
  private static BreadcrumbsInfoProvider findProviderForElement(@NotNull final PsiElement element,
                                                                final BreadcrumbsInfoProvider defaultProvider) {
    final BreadcrumbsInfoProvider provider = getInfoProvider(element.getLanguage());
    return provider == null ? defaultProvider : provider;
  }

  private static PsiElement[] toPsiElementArray(Collection<BreadcrumbsPsiItem> items) {
    final PsiElement[] elements = new PsiElement[items.size()];
    int index = 0;
    for (BreadcrumbsPsiItem item : items) {
      elements[index++] = item.getPsiElement();
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
  private static LinkedList<BreadcrumbsPsiItem> getPresentableLineElements(@NotNull final LogicalPosition position,
                                                                           final VirtualFile file,
                                                                           final Editor editor,
                                                                           final Project project,
                                                                           final BreadcrumbsInfoProvider defaultInfoProvider) {
    final LinkedList<BreadcrumbsPsiItem> result =
      getLineElements(editor.logicalPositionToOffset(position), file, project, defaultInfoProvider);

    if (result == null) return null;

    final PsiElement[] elements = toPsiElementArray(result);
    final CrumbPresentation[] presentations = getCrumbPresentations(elements);
    if (presentations != null) {
      int i = 0;
      for (BreadcrumbsPsiItem item : result) {
        item.setPresentation(presentations[i++]);
      }
    }

    return result;
  }

  @Nullable
  public static PsiElement[] getLinePsiElements(int offset, VirtualFile file, Project project, BreadcrumbsInfoProvider infoProvider) {
    final LinkedList<BreadcrumbsPsiItem> lineElements = getLineElements(offset, file, project, infoProvider);
    return lineElements != null ? toPsiElementArray(lineElements) : null;
  }

  @Nullable
  private static LinkedList<BreadcrumbsPsiItem> getLineElements(final int offset,
                                                                VirtualFile file,
                                                                Project project,
                                                                BreadcrumbsInfoProvider defaultInfoProvider) {
    PsiElement element = findFirstBreadcrumbedElement(offset, file, project, defaultInfoProvider);
    if (element == null) return null;

    final LinkedList<BreadcrumbsPsiItem> result = new LinkedList<>();
    while (element != null) {
      BreadcrumbsInfoProvider provider = findProviderForElement(element, defaultInfoProvider);

      if (provider != null && provider.acceptElement(element)) {
        result.addFirst(new BreadcrumbsPsiItem(element, provider));
      }

      element = (provider != null) ? provider.getParent(element) : element.getParent();
    }
    return result;
  }

  @Nullable
  private static PsiElement findFirstBreadcrumbedElement(final int offset,
                                                         final VirtualFile file,
                                                         final Project project,
                                                         final BreadcrumbsInfoProvider defaultInfoProvider) {
    if (file == null || !file.isValid()) return null;

    PriorityQueue<PsiElement> leafs =
      new PriorityQueue<>(3, (o1, o2) -> o2.getTextRange().getStartOffset() - o1.getTextRange().getStartOffset());
    FileViewProvider viewProvider = findViewProvider(file, project);
    if (viewProvider == null) return null;

    for (final Language language : viewProvider.getLanguages()) {
      ContainerUtil.addIfNotNull(leafs, viewProvider.findElementAt(offset, language));
    }
    while (!leafs.isEmpty()) {
      final PsiElement element = leafs.remove();
      if (!element.isValid()) continue;

      BreadcrumbsInfoProvider provider = findProviderForElement(element, defaultInfoProvider);
      if (provider != null && provider.acceptElement(element)) {
        return element;
      }
      if (!(element instanceof PsiFile)) {
        ContainerUtil.addIfNotNull(leafs, element.getParent());
      }
    }
    return null;
  }

  @Nullable
  private static FileViewProvider findViewProvider(final VirtualFile file, final Project project) {
    if (file == null) return null;
    return PsiManager.getInstance(project).findViewProvider(file);
  }

  private void updateCrumbs(final LogicalPosition position) {
    if (myFile != null && myEditor != null && !myEditor.isDisposed() && !myProject.isDisposed()) {
      if (PsiDocumentManager.getInstance(myProject).isUncommited(myEditor.getDocument())) {
        return;
      }
      myComponent.setItems(getPresentableLineElements(position, myFile, myEditor, myProject, myInfoProvider));
    }
  }

  @Nullable
  public static BreadcrumbsInfoProvider findInfoProvider(@Nullable FileViewProvider viewProvider) {
    if (EditorSettingsExternalizable.getInstance().isBreadcrumbsShown() && viewProvider != null) {
      final Language baseLang = viewProvider.getBaseLanguage();
      BreadcrumbsInfoProvider provider = getInfoProvider(baseLang);
      if (provider != null) {
        return provider;
      }
      for (final Language language : viewProvider.getLanguages()) {
        provider = getInfoProvider(language);
        if (provider != null) {
          return provider;
        }
      }
    }
    return null;
  }

  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public void itemSelected(@NotNull final BreadcrumbsPsiItem item, final int modifiers) {
    final PsiElement psiElement = item.getPsiElement();
    moveEditorCaretTo(psiElement);

    if (BitUtil.isSet(modifiers, Event.SHIFT_MASK) || BitUtil.isSet(modifiers, Event.META_MASK)) {
      final TextRange range = psiElement.getTextRange();
      myEditor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
    }
  }

  @Override
  public void itemHovered(@Nullable BreadcrumbsPsiItem item) {
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
    if (item != null) {
      final TextRange range = item.getPsiElement().getTextRange();
      final TextAttributes attributes = new TextAttributes();
      final CrumbPresentation p = item.getPresentation();
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
  }

  @Nullable
  private static BreadcrumbsInfoProvider getInfoProvider(@NotNull final Language language) {
    for (final BreadcrumbsInfoProvider provider : Extensions.getExtensions(BreadcrumbsInfoProvider.EP_NAME)) {
      for (final Language language1 : provider.getLanguages()) {
        if (language.isKindOf(language1)) {
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
    Font font = editor.getColorsScheme().getFont(EditorFontType.PLAIN);
    return Font.PLAIN == font.getStyle() ? font : font.deriveFont(Font.PLAIN, font.getSize2D());
  }
}
