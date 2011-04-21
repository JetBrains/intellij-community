/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.PriorityQueue;

/**
 * @author spleaner
 */
public class BreadcrumbsXmlWrapper implements BreadcrumbsItemListener<BreadcrumbsPsiItem>, Disposable {
  private final BreadcrumbsComponent<BreadcrumbsPsiItem> myComponent;
  private final Project myProject;
  private Editor myEditor;
  private final VirtualFile myFile;
  private boolean myUserCaretChange;
  private final MergingUpdateQueue myQueue;
  private final BreadcrumbsInfoProvider myInfoProvider;
  private final JPanel myWrapperPanel;

  public static final Key<BreadcrumbsXmlWrapper> BREADCRUMBS_COMPONENT_KEY = new Key<BreadcrumbsXmlWrapper>("BREADCRUMBS_KEY");

  public BreadcrumbsXmlWrapper(@NotNull final Editor editor) {
    myEditor = editor;
    myEditor.putUserData(BREADCRUMBS_COMPONENT_KEY, this);

    final Project project = editor.getProject();
    assert project != null;
    myProject = project;

    Document document = myEditor.getDocument();
    myFile = FileDocumentManager.getInstance().getFile(document);


    final FileStatusManager manager = FileStatusManager.getInstance(project);
    manager.addFileStatusListener(new FileStatusListener() {
      public void fileStatusesChanged() {
        if (myComponent != null && myEditor != null) {
          final Font editorFont = myEditor.getColorsScheme().getFont(EditorFontType.PLAIN);
          myComponent.setFont(editorFont.deriveFont(Font.PLAIN, editorFont.getSize2D()));
          updateCrumbs(myEditor.getCaretModel().getLogicalPosition());
        }
      }

      public void fileStatusChanged(@NotNull final VirtualFile virtualFile) {
      }
    }, this);


    myInfoProvider = findInfoProvider(findViewProvider(myFile, myProject));

    final CaretListener caretListener = new CaretListener() {
      public void caretPositionChanged(final CaretEvent e) {
        if (myUserCaretChange) {
          queueUpdate(editor);
        }

        myUserCaretChange = true;
      }
    };

    editor.getCaretModel().addCaretListener(caretListener);
    Disposer.register(this, new Disposable() {
      public void dispose() {
        editor.getCaretModel().removeCaretListener(caretListener);
      }
    });

    PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
      @Override
      public void propertyChanged(PsiTreeChangeEvent event) {
        PsiFile psiFile = event.getFile();
        VirtualFile file = psiFile == null ? null : psiFile.getVirtualFile();
        if (file != myFile) return;
        queueUpdate(editor);
      }

      @Override
      public void childrenChanged(PsiTreeChangeEvent event) {
        propertyChanged(event);
      }

      @Override
      public void childMoved(PsiTreeChangeEvent event) {
        propertyChanged(event);
      }

      @Override
      public void childReplaced(PsiTreeChangeEvent event) {
        propertyChanged(event);
      }

      @Override
      public void childRemoved(PsiTreeChangeEvent event) {
        propertyChanged(event);
      }

      @Override
      public void childAdded(PsiTreeChangeEvent event) {
        propertyChanged(event);
      }
    },this);

    myComponent = new BreadcrumbsComponent<BreadcrumbsPsiItem>();
    myComponent.addBreadcrumbsItemListener(this);

    final Font editorFont = editor.getColorsScheme().getFont(EditorFontType.PLAIN);
    myComponent.setFont(editorFont.deriveFont(Font.PLAIN, editorFont.getSize2D()));

    final ComponentAdapter resizeListener = new ComponentAdapter() {
      public void componentResized(final ComponentEvent e) {
        queueUpdate(editor);
      }
    };

    myComponent.addComponentListener(resizeListener);
    Disposer.register(this, new Disposable() {
      public void dispose() {
        myComponent.removeComponentListener(resizeListener);
      }
    });

    myQueue = new MergingUpdateQueue("Breadcrumbs.Queue", 200, true, myComponent);
    myQueue.queue(new MyUpdate(this, editor));

    Disposer.register(this, new UiNotifyConnector(myComponent, myQueue));
    Disposer.register(this, myQueue);

    myWrapperPanel = new JPanel();
    myWrapperPanel.setLayout(new BorderLayout());
    myWrapperPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 1, 2));
    myWrapperPanel.setOpaque(false);

    myWrapperPanel.add(myComponent, BorderLayout.CENTER);
  }

  public void queueUpdate(Editor editor) {
    myQueue.cancelAllUpdates();
    myQueue.queue(new MyUpdate(this, editor));
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

    final LinkedList<BreadcrumbsPsiItem> result = new LinkedList<BreadcrumbsPsiItem>();
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

    PriorityQueue<PsiElement> leafs = new PriorityQueue<PsiElement>(3, new Comparator<PsiElement>() {
      public int compare(final PsiElement o1, final PsiElement o2) {
        return o2.getTextRange().getStartOffset() - o1.getTextRange().getStartOffset();
      }
    });
    FileViewProvider viewProvider = findViewProvider(file, project);
    if (viewProvider == null) return null;

    for (final Language language : viewProvider.getLanguages()) {
      ContainerUtil.addIfNotNull(viewProvider.findElementAt(offset, language), leafs);
    }
    while (!leafs.isEmpty()) {
      final PsiElement element = leafs.remove();
      if (!element.isValid()) continue;

      BreadcrumbsInfoProvider provider = findProviderForElement(element, defaultInfoProvider);
      if (provider != null && provider.acceptElement(element)) {
        return element;
      }
      if (!(element instanceof PsiFile)) {
        ContainerUtil.addIfNotNull(element.getParent(), leafs);
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
    if (myFile != null && myEditor != null) {
      if (PsiDocumentManager.getInstance(myProject).isUncommited(myEditor.getDocument())) {
        return;
      }

      myComponent.setItems(getPresentableLineElements(position, myFile, myEditor, myProject, myInfoProvider));
    }
  }

  @Nullable
  public static BreadcrumbsInfoProvider findInfoProvider(@Nullable FileViewProvider viewProvider) {
    BreadcrumbsInfoProvider provider = null;
    if (viewProvider != null) {
      final WebEditorOptions webEditorOptions = WebEditorOptions.getInstance();
      final Language baseLang = viewProvider.getBaseLanguage();
      provider = getInfoProvider(baseLang);
      if (!webEditorOptions.isBreadcrumbsEnabledInXml() && baseLang == XMLLanguage.INSTANCE) return null;
      if (!webEditorOptions.isBreadcrumbsEnabled() && baseLang != XMLLanguage.INSTANCE) return null;
      if (provider == null) {
        for (final Language language : viewProvider.getLanguages()) {
          provider = getInfoProvider(language);
          if (provider != null) {
            break;
          }
        }
      }
    }
    return provider;
  }

  public JComponent getComponent() {
    return myWrapperPanel;
  }

  public void itemSelected(@NotNull final BreadcrumbsPsiItem item, final int modifiers) {
    final PsiElement psiElement = item.getPsiElement();
    moveEditorCaretTo(psiElement);

    if ((modifiers & Event.SHIFT_MASK) == Event.SHIFT_MASK || (modifiers & Event.META_MASK) == Event.META_MASK) {
      final TextRange range = psiElement.getTextRange();
      myEditor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
    }
  }

  @Nullable
  public static BreadcrumbsXmlWrapper getBreadcrumbsComponent(@NotNull Editor editor) {
    return editor.getUserData(BREADCRUMBS_COMPONENT_KEY);
  }

  public void dispose() {
    myEditor.putUserData(BREADCRUMBS_COMPONENT_KEY, null);
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

  private class MyUpdate extends Update {
    private final BreadcrumbsXmlWrapper myBreadcrumbsComponent;
    private final Editor myEditor;

    public MyUpdate(@NonNls final BreadcrumbsXmlWrapper c, @NotNull final Editor editor) {
      super(c);

      myBreadcrumbsComponent = c;
      myEditor = editor;
    }

    public void run() {
      myBreadcrumbsComponent.updateCrumbs(myEditor.getCaretModel().getLogicalPosition());
    }

    public boolean canEat(final Update update) {
      return true;
    }
  }

}
