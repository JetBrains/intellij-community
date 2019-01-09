// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.breadcrumbs;

import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.availability.PsiAvailabilityService;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.intellij.ui.breadcrumbs.BreadcrumbsUtil;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.PriorityQueue;

import static com.intellij.xml.breadcrumbs.BreadcrumbsUtilEx.findProvider;

public class PsiFileBreadcrumbsCollector extends FileBreadcrumbsCollector {
  private final static Logger LOG = Logger.getInstance(PsiFileBreadcrumbsCollector.class);

  private final Project myProject;

  public PsiFileBreadcrumbsCollector(Project project) {
    myProject = project;
  }

  @Override
  public boolean handlesFile(@NotNull VirtualFile virtualFile) {
    return true;
  }

  @Override
  public boolean isShownForFile(@NotNull Editor editor, @NotNull VirtualFile file) {
    return BreadcrumbsUtilEx.findProvider(editor, file) != null;
  }

  @Override
  public void watchForChanges(@NotNull VirtualFile file,
                              @NotNull Editor editor,
                              @NotNull Disposable disposable,
                              @NotNull Runnable changesHandler) {
    PsiManager psiManager = PsiManager.getInstance(myProject);
    psiManager.addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
      @Override
      public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
        PsiFile psiFile = event.getFile();
        VirtualFile changedFile = psiFile == null ? null : psiFile.getVirtualFile();
        if (!Comparing.equal(changedFile, file)) return;
        changesHandler.run();
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
    }, disposable);

  }

  @Override
  public void updateCrumbs(@NotNull VirtualFile virtualFile,
                           @NotNull Editor editor,
                           int offset,
                           ProgressIndicator progressIndicator,
                           @NotNull Consumer<Iterable<? extends Crumb>> consumer) {
    PsiAvailabilityService.getInstance(myProject).performWhenPsiAvailable(editor.getDocument(), () -> {
      consumer.consume(collectBreadcrumbs(virtualFile, editor, offset));
    }, progressIndicator);
  }

  @NotNull
  private Iterable<Crumb> collectBreadcrumbs(VirtualFile file, Editor editor, int offset) {
    BreadcrumbsProvider defaultInfoProvider = findProvider(editor, file);

    Collection<Pair<PsiElement, BreadcrumbsProvider>> pairs =
      getLineElements(editor, offset, file, myProject, defaultInfoProvider, true);

    if (pairs == null) return ContainerUtil.emptyIterable();

    ArrayList<Crumb> result = new ArrayList<>(pairs.size());
    CrumbPresentation[] presentations = getCrumbPresentations(toPsiElementArray(pairs));
    int index = 0;
    for (Pair<PsiElement, BreadcrumbsProvider> pair : pairs) {
      CrumbPresentation presentation = null;
      if (presentations != null && 0 <= index && index < presentations.length) {
        presentation = presentations[index++];
      }
      result.add(new PsiCrumb(pair.first, pair.second, presentation));
    }

    return result;
  }

  @Nullable
  private static CrumbPresentation[] getCrumbPresentations(final PsiElement[] elements) {
    for (BreadcrumbsPresentationProvider provider : BreadcrumbsPresentationProvider.EP_NAME.getExtensionList()) {
      final CrumbPresentation[] presentations = provider.getCrumbPresentations(elements);
      if (presentations != null) {
        return presentations;
      }
    }
    return null;
  }

  @Nullable
  private static Collection<Pair<PsiElement, BreadcrumbsProvider>> getLineElements(Editor editor,
                                                                                   int offset,
                                                                                   VirtualFile file,
                                                                                   Project project,
                                                                                   BreadcrumbsProvider defaultInfoProvider,
                                                                                   boolean checkSettings) {
    PsiElement element = findStartElement(editor, offset, file, project, defaultInfoProvider, checkSettings);
    if (element == null) return null;

    LinkedList<Pair<PsiElement, BreadcrumbsProvider>> result = new LinkedList<>();
    while (element != null) {
      BreadcrumbsProvider provider = findProviderForElement(element, defaultInfoProvider, checkSettings);

      if (provider != null && provider.acceptElement(element)) {
        result.addFirst(Pair.create(element, provider));
      }

      element = getParent(element, provider);
      if (element instanceof PsiDirectory) break;
    }
    return result;
  }

  /**
   * Finds first breadcrumb-rendering element, possibly shifting offset backwards, skipping whitespaces and grabbing previous element
   * This logic solves inconsistency with brace matcher. For example,
   * <pre><code>
   *   class Foo {
   *     public void bar() {
   *
   *     } &lt;caret&gt;
   *   }
   * </code></pre>
   * will highlight bar's braces, looking backwards. So it should include it to breadcrumbs, too.
   */
  @Nullable
  private static PsiElement findStartElement(Editor editor,
                                             int offset,
                                             VirtualFile file,
                                             Project project,
                                             BreadcrumbsProvider defaultInfoProvider,
                                             boolean checkSettings) {
    PsiElement middleElement = findFirstBreadcrumbedElement(offset, file, project, defaultInfoProvider, checkSettings);

    // Let's simulate brace matcher logic of searching brace backwards (see `BraceHighlightingHandler.updateBraces`)
    CharSequence chars = editor.getDocument().getCharsSequence();
    int leftOffset = CharArrayUtil.shiftBackward(chars, offset - 1, "\t ");
    leftOffset = leftOffset >= 0 ? leftOffset : offset - 1;

    PsiElement leftElement = findFirstBreadcrumbedElement(leftOffset, file, project, defaultInfoProvider, checkSettings);
    if (leftElement != null && (middleElement == null || PsiTreeUtil.isAncestor(middleElement, leftElement, true))) {
      return leftElement;
    }
    else {
      return middleElement;
    }
  }

  @Nullable
  private static PsiElement findFirstBreadcrumbedElement(final int offset,
                                                         final VirtualFile file,
                                                         final Project project,
                                                         final BreadcrumbsProvider defaultInfoProvider,
                                                         boolean checkSettings) {
    if (file == null || !file.isValid() || file.isDirectory()) return null;

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
    FileViewProvider viewProvider = BreadcrumbsUtilEx.findViewProvider(file, project);
    if (viewProvider == null) return null;

    for (final Language language : viewProvider.getLanguages()) {
      ContainerUtil.addIfNotNull(leafs, viewProvider.findElementAt(offset, language));
    }
    while (!leafs.isEmpty()) {
      final PsiElement element = leafs.remove();
      if (!element.isValid()) continue;

      BreadcrumbsProvider provider = findProviderForElement(element, defaultInfoProvider, checkSettings);
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
  private static BreadcrumbsProvider findProviderForElement(@NotNull PsiElement element, BreadcrumbsProvider defaultProvider, boolean checkSettings) {
    Language language = element.getLanguage();
    if (checkSettings && !EditorSettingsExternalizable.getInstance().isBreadcrumbsShownFor(language.getID())) return defaultProvider;
    BreadcrumbsProvider provider = BreadcrumbsUtil.getInfoProvider(language);
    return provider == null ? defaultProvider : provider;
  }

  private static PsiElement[] toPsiElementArray(Collection<? extends Pair<PsiElement, BreadcrumbsProvider>> pairs) {
    PsiElement[] elements = new PsiElement[pairs.size()];
    int index = 0;
    for (Pair<PsiElement, BreadcrumbsProvider> pair : pairs) {
      elements[index++] = pair.first;
    }
    return elements;
  }

  @Nullable
  public static PsiElement[] getLinePsiElements(Editor editor,
                                                int offset,
                                                VirtualFile file,
                                                Project project,
                                                BreadcrumbsProvider infoProvider) {
    Collection<Pair<PsiElement, BreadcrumbsProvider>> pairs = getLineElements(editor, offset, file, project, infoProvider, false);
    return pairs == null ? null : toPsiElementArray(pairs);
  }
}
