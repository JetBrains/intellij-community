/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.navigation;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Factory;
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
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private static final NotNullFunction<PsiElement, Collection<? extends PsiElement>> DEFAULT_PSI_CONVERTOR =
    element -> ContainerUtil.createMaybeSingletonList(element);

  private final Icon myIcon;
  private final NotNullFunction<T, Collection<? extends PsiElement>> myConverter;

  protected NotNullLazyValue<Collection<T>> myTargets;
  protected boolean myLazy;
  private String myTooltipText;
  private String myPopupTitle;
  private String myEmptyText;
  private String myTooltipTitle;
  private GutterIconRenderer.Alignment myAlignment = GutterIconRenderer.Alignment.CENTER;
  private Computable<PsiElementListCellRenderer> myCellRenderer;
  private NullableFunction<T, String> myNamer = ElementPresentationManager.namer();
  protected final NotNullFunction<T, Collection<? extends GotoRelatedItem>> myGotoRelatedItemProvider;
  public static final NotNullFunction<DomElement, Collection<? extends PsiElement>> DEFAULT_DOM_CONVERTOR =
    o -> ContainerUtil.createMaybeSingletonList(o.getXmlElement());
  public static final NotNullFunction<DomElement, Collection<? extends GotoRelatedItem>> DOM_GOTO_RELATED_ITEM_PROVIDER = dom -> {
    if (dom.getXmlElement() != null) {
      return Collections.singletonList(new DomGotoRelatedItem(dom));
    }
    return Collections.emptyList();
  };
  public static final NotNullFunction<PsiElement, Collection<? extends GotoRelatedItem>> PSI_GOTO_RELATED_ITEM_PROVIDER =
    dom -> Collections.singletonList(new GotoRelatedItem(dom, "XML"));

  protected NavigationGutterIconBuilder(@NotNull final Icon icon, @NotNull NotNullFunction<T, Collection<? extends PsiElement>> converter) {
    this(icon, converter, null);
  }

  protected NavigationGutterIconBuilder(@NotNull final Icon icon,
                                        @NotNull NotNullFunction<T, Collection<? extends PsiElement>> converter,
                                        final @Nullable NotNullFunction<T, Collection<? extends GotoRelatedItem>> gotoRelatedItemProvider) {
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
                                                          @NotNull NotNullFunction<T, Collection<? extends PsiElement>> converter) {
    return create(icon, converter, null);
  }

  @NotNull
  public static <T> NavigationGutterIconBuilder<T> create(@NotNull final Icon icon,
                                                          @NotNull NotNullFunction<T, Collection<? extends PsiElement>> converter,
                                                          final @Nullable NotNullFunction<T, Collection<? extends GotoRelatedItem>> gotoRelatedItemProvider) {
    return new NavigationGutterIconBuilder<T>(icon, converter, gotoRelatedItemProvider);
  }

  public NavigationGutterIconBuilder<T> setTarget(@Nullable T target) {
    return setTargets(ContainerUtil.createMaybeSingletonList(target));
  }

  public NavigationGutterIconBuilder<T> setTargets(@NotNull T... targets) {
    return setTargets(Arrays.asList(targets));
  }

  public NavigationGutterIconBuilder<T> setTargets(@NotNull final NotNullLazyValue<Collection<? extends T>> targets) {
    //noinspection unchecked
    myTargets = (NotNullLazyValue)targets;
    myLazy = true;
    return this;
  }

  public NavigationGutterIconBuilder<T> setTargets(@NotNull final Collection<? extends T> targets) {
    //noinspection unchecked
    myTargets = NotNullLazyValue.createConstantValue((Collection<T>)targets);
    return this;
  }

  public NavigationGutterIconBuilder<T> setTooltipText(@NotNull String tooltipText) {
    myTooltipText = tooltipText;
    return this;
  }

  public NavigationGutterIconBuilder<T> setAlignment(@NotNull final GutterIconRenderer.Alignment alignment) {
    myAlignment = alignment;
    return this;
  }

  public NavigationGutterIconBuilder<T> setPopupTitle(@NotNull @Nls(capitalization = Nls.Capitalization.Title) String popupTitle) {
    myPopupTitle = popupTitle;
    return this;
  }

  public NavigationGutterIconBuilder<T> setEmptyPopupText(@NotNull String emptyText) {
    myEmptyText = emptyText;
    return this;
  }

  public NavigationGutterIconBuilder<T> setTooltipTitle(@NotNull final String tooltipTitle) {
    myTooltipTitle = tooltipTitle;
    return this;
  }

  public NavigationGutterIconBuilder<T> setNamer(@NotNull NullableFunction<T, String> namer) {
    myNamer = namer;
    return this;
  }

  public NavigationGutterIconBuilder<T> setCellRenderer(@NotNull final PsiElementListCellRenderer cellRenderer) {
    myCellRenderer = new Computable.PredefinedValueComputable<PsiElementListCellRenderer>(cellRenderer);
    return this;
  }

  @Nullable
  public Annotation install(@NotNull DomElementAnnotationHolder holder, @Nullable DomElement element) {
    if (!myLazy && myTargets.getValue().isEmpty() || element == null) return null;
    return doInstall(holder.createAnnotation(element, HighlightSeverity.INFORMATION, null), element.getManager().getProject());
  }

  @Nullable
  public Annotation install(@NotNull AnnotationHolder holder, @Nullable PsiElement element) {
    if (!myLazy && myTargets.getValue().isEmpty() || element == null) return null;
    return doInstall(holder.createInfoAnnotation(element, null), element.getProject());
  }

  private Annotation doInstall(@NotNull Annotation annotation, @NotNull Project project) {
    final MyNavigationGutterIconRenderer renderer = createGutterIconRenderer(project);
    annotation.setGutterIconRenderer(renderer);
    annotation.setNeedsUpdateOnTyping(false);
    return annotation;
  }

  @NotNull
  public RelatedItemLineMarkerInfo<PsiElement> createLineMarkerInfo(@NotNull PsiElement element) {
    final MyNavigationGutterIconRenderer renderer = createGutterIconRenderer(element.getProject());
    final String tooltip = renderer.getTooltipText();
    NotNullLazyValue<Collection<? extends GotoRelatedItem>> gotoTargets = getGotoTargets();
    return new RelatedItemLineMarkerInfo<>(element, element.getTextRange(), renderer.getIcon(), Pass.LINE_MARKERS,
                                           tooltip == null ? null : new ConstantFunction<>(tooltip),
                                           renderer.isNavigateAction() ? renderer : null, renderer.getAlignment(),
                                           gotoTargets);
  }

  @NotNull
  protected NotNullLazyValue<Collection<? extends GotoRelatedItem>> getGotoTargets() {
    return createGotoTargetsThunk(myLazy, myGotoRelatedItemProvider, evaluateAndForget(myTargets));
  }

  private static <T> NotNullLazyValue<Collection<? extends GotoRelatedItem>> createGotoTargetsThunk(boolean lazy,
                                                                                                    final NotNullFunction<T, Collection<? extends GotoRelatedItem>> gotoRelatedItemProvider,
                                                                                                    final Factory<Collection<T>> factory) {
    if (gotoRelatedItemProvider == null) {
      return NotNullLazyValue.createConstantValue(Collections.emptyList());
    }

    if (lazy) {
      return new NotNullLazyValue<Collection<? extends GotoRelatedItem>>() {
        @NotNull
        @Override
        protected Collection<? extends GotoRelatedItem> compute() {
          return ContainerUtil.concat(factory.create(), gotoRelatedItemProvider);
        }
      };
    }
    Collection<GotoRelatedItem> concat = ContainerUtil.concat(factory.create(), gotoRelatedItemProvider);
    return NotNullLazyValue.createConstantValue(concat);
  }

  private void checkBuilt() {
    assert myTargets != null : "Must have called .setTargets() before calling create()";
  }

  protected static <T> Factory<T> evaluateAndForget(NotNullLazyValue<T> lazyValue) {

    final Ref<NotNullLazyValue<T>> ref = Ref.create(lazyValue);
    return new Factory<T>() {
      volatile T result;

      @Override
      public T create() {
        if (result == null) {
          result = ref.get().getValue();
          ref.set(null);
        }
        return result;
      }
    };
  }

  protected MyNavigationGutterIconRenderer createGutterIconRenderer(@NotNull final Project project) {
    checkBuilt();

    NotNullLazyValue<List<SmartPsiElementPointer>> pointers = createPointersThunk(myLazy, project, evaluateAndForget(myTargets),
                                                                                  myConverter);

    final boolean empty = isEmpty();

    if (myTooltipText == null && !myLazy) {
      final SortedSet<String> names = new TreeSet<String>();
      for (T t : myTargets.getValue()) {
        final String text = myNamer.fun(t);
        if (text != null) {
          names.add(MessageFormat.format(PATTERN, text));
        }
      }
      @NonNls StringBuilder sb = new StringBuilder("<html><body>");
      if (myTooltipTitle != null) {
        sb.append(myTooltipTitle).append("<br>");
      }
      for (String name : names) {
        sb.append(name).append("<br>");
      }
      sb.append("</body></html>");
      myTooltipText = sb.toString();
    }

    Computable<PsiElementListCellRenderer> renderer =
      myCellRenderer == null ? (Computable<PsiElementListCellRenderer>)() -> new DefaultPsiElementCellRenderer() : myCellRenderer;
    return new MyNavigationGutterIconRenderer(this, myAlignment, myIcon, myTooltipText, pointers, renderer, empty);
  }

  @NotNull
  private static <T> NotNullLazyValue<List<SmartPsiElementPointer>> createPointersThunk(boolean lazy,
                                                                                        final Project project,
                                                                                        final Factory<Collection<T>> targets,
                                                                                        final NotNullFunction<T, Collection<? extends PsiElement>> converter) {
    if (!lazy) {
      return NotNullLazyValue.createConstantValue(calcPsiTargets(project, targets.create(), converter));
    }

    return new NotNullLazyValue<List<SmartPsiElementPointer>>() {
      @Override
      @NotNull
      public List<SmartPsiElementPointer> compute() {
        return calcPsiTargets(project, targets.create(), converter);
      }
    };
  }

  private static <T> List<SmartPsiElementPointer> calcPsiTargets(Project project, Collection<? extends T> targets,
                                                                 NotNullFunction<T, Collection<? extends PsiElement>> converter) {
    SmartPointerManager manager = SmartPointerManager.getInstance(project);
    Set<PsiElement> elements = new THashSet<PsiElement>();
    final List<SmartPsiElementPointer> list = new ArrayList<SmartPsiElementPointer>(targets.size());
    for (final T target : targets) {
      for (final PsiElement psiElement : converter.fun(target)) {
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

    Set<PsiElement> elements = new THashSet<PsiElement>();
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
    private final String myTooltipText;
    private final boolean myEmpty;

    public MyNavigationGutterIconRenderer(@NotNull NavigationGutterIconBuilder builder,
                                          final Alignment alignment,
                                          final Icon icon,
                                          @Nullable final String tooltipText,
                                          @NotNull NotNullLazyValue<List<SmartPsiElementPointer>> pointers,
                                          Computable<PsiElementListCellRenderer> cellRenderer,
                                          boolean empty) {
      super(builder.myPopupTitle, builder.myEmptyText, cellRenderer, pointers);
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

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!super.equals(o)) return false;

      final MyNavigationGutterIconRenderer that = (MyNavigationGutterIconRenderer)o;

      if (myAlignment != that.myAlignment) return false;
      if (myIcon != null ? !myIcon.equals(that.myIcon) : that.myIcon != null) return false;
      if (myTooltipText != null ? !myTooltipText.equals(that.myTooltipText) : that.myTooltipText != null) return false;

      return true;
    }

    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (myAlignment != null ? myAlignment.hashCode() : 0);
      result = 31 * result + (myIcon != null ? myIcon.hashCode() : 0);
      result = 31 * result + (myTooltipText != null ? myTooltipText.hashCode() : 0);
      return result;
    }
  }

  private static class MyPsiElementRelatedItemLineMarkerInfo extends RelatedItemLineMarkerInfo<PsiElement> {
    public MyPsiElementRelatedItemLineMarkerInfo(PsiElement element,
                                                 NavigationGutterIconRenderer renderer,
                                                 String tooltip,
                                                 NotNullLazyValue<Collection<? extends GotoRelatedItem>> gotoTargets) {
      super(element, element.getTextRange(), renderer.getIcon(), Pass.LINE_MARKERS, tooltip == null
                                                                                    ? null
                                                                                    : new ConstantFunction<>(tooltip),
            renderer.isNavigateAction() ? renderer : null, renderer.getAlignment(), gotoTargets);
    }
  }
}
