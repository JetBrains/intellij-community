// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsContexts.PopupContent;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import com.intellij.openapi.util.NlsContexts.Tooltip;
import com.intellij.openapi.util.NotNullFactory;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.ConstantFunction;
import com.intellij.util.NotNullFunction;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.text.MessageFormat;
import java.util.*;

/**
 * DOM-specific builder for {@link GutterIconRenderer}
 * and {@link com.intellij.codeInsight.daemon.LineMarkerInfo}.
 *
 * @author peter
 */
public class NavigationGutterIconBuilder<T> {
  @NonNls private static final String PATTERN = "&nbsp;&nbsp;&nbsp;&nbsp;{0}";
  protected static final NotNullFunction<PsiElement,Collection<? extends PsiElement>> DEFAULT_PSI_CONVERTOR =
    ContainerUtil::createMaybeSingletonList;

  protected final Icon myIcon;
  private final NotNullFunction<? super T, ? extends Collection<? extends PsiElement>> myConverter;

  protected NotNullLazyValue<Collection<? extends T>> myTargets;
  protected boolean myLazy;
  protected @Tooltip String myTooltipText;
  protected @PopupTitle String myPopupTitle;
  protected @PopupContent String myEmptyText;
  protected @PopupTitle String myTooltipTitle;
  protected GutterIconRenderer.Alignment myAlignment = GutterIconRenderer.Alignment.CENTER;
  private Computable<PsiElementListCellRenderer<?>> myCellRenderer;
  private @NotNull NullableFunction<? super T, String> myNamer = ElementPresentationManager.namer();
  private final NotNullFunction<? super T, ? extends Collection<? extends GotoRelatedItem>> myGotoRelatedItemProvider;
  public static final NotNullFunction<DomElement, Collection<? extends PsiElement>> DEFAULT_DOM_CONVERTOR =
    o -> ContainerUtil.createMaybeSingletonList(o.getXmlElement());
  public static final NotNullFunction<DomElement, Collection<? extends GotoRelatedItem>> DOM_GOTO_RELATED_ITEM_PROVIDER = dom -> {
    if (dom.getXmlElement() != null) {
      return Collections.singletonList(new DomGotoRelatedItem(dom));
    }
    return Collections.emptyList();
  };
  protected static final NotNullFunction<PsiElement, Collection<? extends GotoRelatedItem>> PSI_GOTO_RELATED_ITEM_PROVIDER =
    dom -> Collections.singletonList(new GotoRelatedItem(dom, InspectionsBundle.message("xml.goto.group")));

  protected NavigationGutterIconBuilder(@NotNull final Icon icon, @NotNull NotNullFunction<? super T, ? extends Collection<? extends PsiElement>> converter) {
    this(icon, converter, null);
  }

  protected NavigationGutterIconBuilder(@NotNull final Icon icon,
                                        @NotNull NotNullFunction<? super T, ? extends Collection<? extends PsiElement>> converter,
                                        @Nullable final NotNullFunction<? super T, ? extends Collection<? extends GotoRelatedItem>> gotoRelatedItemProvider) {
    myIcon = icon;
    myConverter = converter;
    myGotoRelatedItemProvider = gotoRelatedItemProvider;
  }

  @NotNull
  public static NavigationGutterIconBuilder<PsiElement> create(@NotNull final Icon icon) {
    return create(icon, DEFAULT_PSI_CONVERTOR, PSI_GOTO_RELATED_ITEM_PROVIDER);
  }

  @NotNull
  public static <T> NavigationGutterIconBuilder<T> create(@NotNull final Icon icon,
                                                          @NotNull NotNullFunction<? super T, ? extends Collection<? extends PsiElement>> converter) {
    return create(icon, converter, null);
  }

  @NotNull
  public static <T> NavigationGutterIconBuilder<T> create(@NotNull final Icon icon,
                                                          @NotNull NotNullFunction<? super T, ? extends Collection<? extends PsiElement>> converter,
                                                          @Nullable final NotNullFunction<? super T, ? extends Collection<? extends GotoRelatedItem>> gotoRelatedItemProvider) {
    return new NavigationGutterIconBuilder<>(icon, converter, gotoRelatedItemProvider);
  }

  @NotNull
  public NavigationGutterIconBuilder<T> setTarget(@Nullable T target) {
    return setTargets(ContainerUtil.createMaybeSingletonList(target));
  }

  @SafeVarargs
  @NotNull
  public final NavigationGutterIconBuilder<T> setTargets(T @NotNull ... targets) {
    return setTargets(Arrays.asList(targets));
  }

  @NotNull
  public NavigationGutterIconBuilder<T> setTargets(@NotNull final NotNullLazyValue<Collection<? extends T>> targets) {
    myTargets = targets;
    myLazy = true;
    return this;
  }

  @NotNull
  public NavigationGutterIconBuilder<T> setTargets(@NotNull final Collection<? extends T> targets) {
    if (ContainerUtil.containsIdentity(targets, null)) {
      throw new IllegalArgumentException("Must not pass collection with null target but got: " + targets);
    }
    myTargets = NotNullLazyValue.createConstantValue(targets);
    return this;
  }

  @NotNull
  public NavigationGutterIconBuilder<T> setTooltipText(@NotNull @Tooltip String tooltipText) {
    myTooltipText = tooltipText;
    return this;
  }

  @NotNull
  public NavigationGutterIconBuilder<T> setAlignment(@NotNull final GutterIconRenderer.Alignment alignment) {
    myAlignment = alignment;
    return this;
  }

  @NotNull
  public NavigationGutterIconBuilder<T> setPopupTitle(@NotNull @PopupTitle String popupTitle) {
    myPopupTitle = popupTitle;
    return this;
  }

  @NotNull
  public NavigationGutterIconBuilder<T> setEmptyPopupText(@NotNull @PopupContent String emptyText) {
    myEmptyText = emptyText;
    return this;
  }

  @NotNull
  public NavigationGutterIconBuilder<T> setTooltipTitle(final @NotNull @PopupTitle String tooltipTitle) {
    myTooltipTitle = tooltipTitle;
    return this;
  }

  @NotNull
  public NavigationGutterIconBuilder<T> setNamer(@NotNull NullableFunction<? super T, String> namer) {
    myNamer = namer;
    return this;
  }

  /**
   * This method may lead to a deadlock when used from pooled thread, e.g. from
   * {@link com.intellij.codeInsight.daemon.LineMarkerProvider#collectSlowLineMarkers(List, Collection)}.
   * {@link PsiElementListCellRenderer} is a UI component that acquires Swing tree lock on init.
   *
   * @deprecated Use {@link #setCellRenderer(Computable)} instead, then renderer will be instantiated lazily and from EDT
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.3")
  @NotNull
  public NavigationGutterIconBuilder<T> setCellRenderer(@NotNull final PsiElementListCellRenderer cellRenderer) {
    myCellRenderer = new Computable.PredefinedValueComputable<>(cellRenderer);
    return this;
  }

  /**
   * @param cellRendererProvider list cell renderer for navigation popup
   */
  public @NotNull NavigationGutterIconBuilder<T> setCellRenderer(@NotNull Computable<PsiElementListCellRenderer<?>> cellRendererProvider) {
    myCellRenderer = cellRendererProvider;
    return this;
  }

  @Nullable
  public Annotation install(@NotNull DomElementAnnotationHolder holder, @Nullable DomElement element) {
    if (!myLazy && myTargets.getValue().isEmpty() || element == null) return null;
    return doInstall(holder.createAnnotation(element, HighlightSeverity.INFORMATION, null), element.getManager().getProject());
  }

  /**
   * @deprecated Use {{@link #createGutterIcon(AnnotationHolder, PsiElement)}} instead
   */
  @Nullable
  @Deprecated
  public Annotation install(@NotNull AnnotationHolder holder, @Nullable PsiElement element) {
    if (!myLazy && myTargets.getValue().isEmpty() || element == null) return null;
    return doInstall(holder.createInfoAnnotation(element, null), element.getProject());
  }

  public void createGutterIcon(@NotNull AnnotationHolder holder, @Nullable PsiElement element) {
    if (!myLazy && myTargets.getValue().isEmpty() || element == null) return;
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element).gutterIconRenderer(createGutterIconRenderer(
      element.getProject())).needsUpdateOnTyping(false).create();
  }

  @NotNull
  private Annotation doInstall(@NotNull Annotation annotation, @NotNull Project project) {
    NavigationGutterIconRenderer renderer = createGutterIconRenderer(project);
    annotation.setGutterIconRenderer(renderer);
    annotation.setNeedsUpdateOnTyping(false);
    return annotation;
  }

  @NotNull
  public RelatedItemLineMarkerInfo<PsiElement> createLineMarkerInfo(@NotNull PsiElement element) {
    NavigationGutterIconRenderer renderer = createGutterIconRenderer(element.getProject());
    return createLineMarkerInfo(element, renderer.isNavigateAction() ? renderer : null);
  }

  @NotNull
  public RelatedItemLineMarkerInfo<PsiElement> createLineMarkerInfo(@NotNull PsiElement element,
                                                                    @Nullable GutterIconNavigationHandler<PsiElement> navigationHandler) {
    NavigationGutterIconRenderer renderer = createGutterIconRenderer(element.getProject());
    String tooltip = renderer.getTooltipText();
    return new RelatedItemLineMarkerInfo<>(
      element,
      element.getTextRange(),
      renderer.getIcon(),
      tooltip == null ? null : new ConstantFunction<>(tooltip),
      navigationHandler,
      renderer.getAlignment(),
      () -> computeGotoTargets());
  }

  @NotNull
  protected Collection<GotoRelatedItem> computeGotoTargets() {
    if (myTargets == null || myGotoRelatedItemProvider == null) return Collections.emptyList();
    NotNullFactory<Collection<? extends T>> factory = evaluateAndForget(myTargets);
    return ContainerUtil.concat(factory.create(), myGotoRelatedItemProvider);
  }

  private void checkBuilt() {
    assert myTargets != null : "Must have called .setTargets() before calling create()";
  }

  @NotNull
  private static <T> NotNullFactory<T> evaluateAndForget(@NotNull NotNullLazyValue<T> lazyValue) {
    final Ref<NotNullLazyValue<T>> ref = Ref.create(lazyValue);
    return new NotNullFactory<>() {
      volatile T value;

      @NotNull
      @Override
      public T create() {
        T result = value;
        if (result == null) {
          value = result = ref.get().getValue();
          ref.set(null);
        }
        return result;
      }
    };
  }

  @NotNull
  protected NavigationGutterIconRenderer createGutterIconRenderer(@NotNull final Project project) {
    checkBuilt();

    NotNullFactory<Collection<? extends T>> factory = evaluateAndForget(myTargets);
    NotNullLazyValue<List<SmartPsiElementPointer<?>>> pointers = createPointersThunk(myLazy, project, factory, myConverter);

    final boolean empty = isEmpty();

    if (myTooltipText == null && !myLazy) {
      final SortedSet<String> names = new TreeSet<>();
      for (T t : myTargets.getValue()) {
        final String text = myNamer.fun(t);
        if (text != null) {
          names.add(MessageFormat.format(PATTERN, text));
        }
      }
      @Nls StringBuilder sb = new StringBuilder("<html><body>");
      if (myTooltipTitle != null) {
        sb.append(myTooltipTitle).append("<br>");
      }
      for (String name : names) {
        sb.append(name).append("<br>");
      }
      sb.append("</body></html>");
      myTooltipText = sb.toString();
    }

    Computable<PsiElementListCellRenderer<?>> renderer =
      myCellRenderer == null ? DefaultPsiElementCellRenderer::new : myCellRenderer;
    return createGutterIconRenderer(pointers, renderer, empty);
  }

  @NotNull
  protected NavigationGutterIconRenderer createGutterIconRenderer(@NotNull NotNullLazyValue<List<SmartPsiElementPointer<?>>> pointers,
                                                                  @NotNull Computable<PsiElementListCellRenderer<?>> renderer,
                                                                  boolean empty) {
    if (myLazy) {
      return createLazyGutterIconRenderer(pointers, renderer, empty);
    }
    return new MyNavigationGutterIconRenderer(this, myAlignment, myIcon, myTooltipText, pointers, renderer, empty);
  }

  @NotNull
  protected NavigationGutterIconRenderer createLazyGutterIconRenderer(@NotNull NotNullLazyValue<List<SmartPsiElementPointer<?>>> pointers,
                                                                      @NotNull Computable<PsiElementListCellRenderer<?>> renderer,
                                                                      boolean empty) {
    return new MyNavigationGutterIconRenderer(this, myAlignment, myIcon, myTooltipText, pointers, renderer, empty, true);
  }

  @NotNull
  private static <T> NotNullLazyValue<List<SmartPsiElementPointer<?>>> createPointersThunk(boolean lazy,
                                                                                        final Project project,
                                                                                        final NotNullFactory<? extends Collection<? extends T>> targets,
                                                                                        final NotNullFunction<? super T, ? extends Collection<? extends PsiElement>> converter) {
    if (!lazy) {
      return NotNullLazyValue.createConstantValue(calcPsiTargets(project, targets.create(), converter));
    }

    return NotNullLazyValue.lazy(() -> calcPsiTargets(project, targets.create(), converter));
  }

  @NotNull
  private static <T> List<SmartPsiElementPointer<?>> calcPsiTargets(@NotNull Project project,
                                                                 @NotNull Collection<? extends T> targets,
                                                                 @NotNull NotNullFunction<? super T, ? extends Collection<? extends PsiElement>> converter) {
    SmartPointerManager manager = SmartPointerManager.getInstance(project);
    Set<PsiElement> elements = new HashSet<>();
    final List<SmartPsiElementPointer<?>> list = new ArrayList<>(targets.size());
    for (final T target : targets) {
      for (final PsiElement psiElement : converter.fun(target)) {
        if (psiElement == null) {
          throw new IllegalArgumentException(converter + " returned null element");
        }

        if (elements.add(psiElement) && psiElement.isValid()) {
          list.add(manager.createSmartPsiElementPointer(psiElement));
        }
      }
    }
    return list;
  }

  private boolean isEmpty() {
    if (myLazy) {
      return false;
    }

    Set<PsiElement> elements = new HashSet<>();
    Collection<? extends T> targets = myTargets.getValue();
    for (final T target : targets) {
      for (final PsiElement psiElement : myConverter.fun(target)) {
        if (elements.add(psiElement)) {
          return false;
        }
      }
    }
    return true;
  }

  private static class MyNavigationGutterIconRenderer extends NavigationGutterIconRenderer {
    private final Alignment myAlignment;
    private final Icon myIcon;
    private final @Tooltip String myTooltipText;
    private final boolean myEmpty;

    MyNavigationGutterIconRenderer(@NotNull NavigationGutterIconBuilder<?> builder,
                                   @NotNull Alignment alignment,
                                   final Icon icon,
                                   @Nullable final @Tooltip String tooltipText,
                                   @NotNull NotNullLazyValue<List<SmartPsiElementPointer<?>>> pointers,
                                   @NotNull Computable<PsiElementListCellRenderer<?>> cellRenderer,
                                   boolean empty) {
      super(builder.myPopupTitle, builder.myEmptyText, cellRenderer, pointers);
      myAlignment = alignment;
      myIcon = icon;
      myTooltipText = tooltipText;
      myEmpty = empty;
    }

    MyNavigationGutterIconRenderer(@NotNull NavigationGutterIconBuilder<?> builder,
                                   @NotNull Alignment alignment,
                                   Icon icon,
                                   @Nullable @Tooltip String tooltipText,
                                   @NotNull NotNullLazyValue<List<SmartPsiElementPointer<?>>> pointers,
                                   @NotNull Computable<PsiElementListCellRenderer<?>> cellRenderer,
                                   boolean empty,
                                   boolean computeTargetsInBackground) {
      super(builder.myPopupTitle, builder.myEmptyText, cellRenderer, pointers, computeTargetsInBackground);
      myAlignment = alignment;
      myIcon = icon;
      myTooltipText = tooltipText;
      myEmpty = empty;
    }

    @Override
    public boolean isNavigateAction() {
      return !myEmpty;
    }

    @Override
    @NotNull
    public Icon getIcon() {
      return myIcon;
    }

    @Override
    @Nullable
    public String getTooltipText() {
      return myTooltipText;
    }

    @NotNull
    @Override
    public Alignment getAlignment() {
      return myAlignment;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!super.equals(o)) return false;

      final MyNavigationGutterIconRenderer that = (MyNavigationGutterIconRenderer)o;

      if (myAlignment != that.myAlignment) return false;
      if (myIcon != null ? !myIcon.equals(that.myIcon) : that.myIcon != null) return false;
      if (myTooltipText != null ? !myTooltipText.equals(that.myTooltipText) : that.myTooltipText != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (myAlignment != null ? myAlignment.hashCode() : 0);
      result = 31 * result + (myIcon != null ? myIcon.hashCode() : 0);
      result = 31 * result + (myTooltipText != null ? myTooltipText.hashCode() : 0);
      return result;
    }
  }
}
