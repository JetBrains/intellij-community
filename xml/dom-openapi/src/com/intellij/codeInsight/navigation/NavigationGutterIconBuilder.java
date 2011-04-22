/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.util.NotNullLazyValue;
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.text.MessageFormat;
import java.util.*;

/**
 * DOM-specific builder for {@link com.intellij.openapi.editor.markup.GutterIconRenderer}
 * and {@link com.intellij.codeInsight.daemon.LineMarkerInfo}.
 *
 * @author peter
 */
public class NavigationGutterIconBuilder<T> {
  @NonNls private static final String PATTERN = "&nbsp;&nbsp;&nbsp;&nbsp;{0}";
  private static final NotNullFunction<PsiElement,Collection<? extends PsiElement>> DEFAULT_PSI_CONVERTOR = new NotNullFunction<PsiElement, Collection<? extends PsiElement>>() {
    @NotNull
    public Collection<? extends PsiElement> fun(final PsiElement element) {
      return ContainerUtil.createMaybeSingletonList(element);
    }
  };

  private final Icon myIcon;
  private final NotNullFunction<T,Collection<? extends PsiElement>> myConverter;

  private NotNullLazyValue<Collection<? extends T>> myTargets;
  private boolean myLazy;
  private String myTooltipText;
  private String myPopupTitle;
  private String myEmptyText;
  private String myTooltipTitle;
  private GutterIconRenderer.Alignment myAlignment = GutterIconRenderer.Alignment.CENTER;
  private PsiElementListCellRenderer myCellRenderer;
  private NullableFunction<T,String> myNamer = ElementPresentationManager.namer();
  public static final NotNullFunction<DomElement,Collection<? extends PsiElement>> DEFAULT_DOM_CONVERTOR = new NotNullFunction<DomElement, Collection<? extends PsiElement>>() {
    @NotNull
    public Collection<? extends PsiElement> fun(final DomElement o) {
      return ContainerUtil.createMaybeSingletonList(o.getXmlElement());
    }
  };
  public static final NotNullFunction<DomElement, Collection<? extends GotoRelatedItem>> DOM_GOTO_RELATED_ITEM_PROVIDER = new NotNullFunction<DomElement, Collection<? extends GotoRelatedItem>>() {
    @NotNull
    @Override
    public Collection<? extends GotoRelatedItem> fun(DomElement dom) {
      if (dom.getXmlElement() != null) {
        return Collections.singletonList(new DomGotoRelatedItem(dom));
      }
      return Collections.emptyList();
    }
  };

  protected NavigationGutterIconBuilder(@NotNull final Icon icon, @NotNull NotNullFunction<T,Collection<? extends PsiElement>> converter) {
    myIcon = icon;
    myConverter = converter;
  }

  public static NavigationGutterIconBuilder<PsiElement> create(@NotNull final Icon icon) {
    return create(icon, DEFAULT_PSI_CONVERTOR);
  }

  public static <T> NavigationGutterIconBuilder<T> create(@NotNull final Icon icon, @NotNull NotNullFunction<T, Collection<? extends PsiElement>> converter) {
    return new NavigationGutterIconBuilder<T>(icon, converter);
  }

  public NavigationGutterIconBuilder<T> setTarget(@Nullable T target) {
    return setTargets(ContainerUtil.createMaybeSingletonList(target));
  }

  public NavigationGutterIconBuilder<T> setTargets(@NotNull T... targets) {
    return setTargets(Arrays.asList(targets));
  }

  public NavigationGutterIconBuilder<T> setTargets(@NotNull final NotNullLazyValue<Collection<? extends T>> targets) {
    myTargets = targets;
    myLazy = true;
    return this;
  }

  public NavigationGutterIconBuilder<T> setTargets(@NotNull final Collection<? extends T> targets) {
    myTargets = new NotNullLazyValue<Collection<? extends T>>() {
      @NotNull
      public Collection<? extends T> compute() {
        return targets;
      }
    };
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

  public NavigationGutterIconBuilder<T> setPopupTitle(@NotNull String popupTitle) {
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

  public NavigationGutterIconBuilder<T> setNamer(@NotNull NullableFunction<T,String> namer) {
    myNamer = namer;
    return this;
  }

  public NavigationGutterIconBuilder<T> setCellRenderer(@NotNull final PsiElementListCellRenderer cellRenderer) {
    myCellRenderer = cellRenderer;
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

  public RelatedItemLineMarkerInfo<PsiElement> createLineMarkerInfo(@NotNull PsiElement element) {
    return createLineMarkerInfo(element, null);
  }

  public RelatedItemLineMarkerInfo<PsiElement> createLineMarkerInfo(@NotNull PsiElement element,
                                                                    @Nullable final NotNullFunction<T, Collection<? extends GotoRelatedItem>> gotoRelatedItemProvider) {
    final MyNavigationGutterIconRenderer renderer = createGutterIconRenderer(element.getProject());
    final String tooltip = renderer.getTooltipText();
    NotNullLazyValue<Collection<? extends GotoRelatedItem>> gotoTargets = new NotNullLazyValue<Collection<? extends GotoRelatedItem>>() {
      @NotNull
      @Override
      protected Collection<? extends GotoRelatedItem> compute() {
        if (gotoRelatedItemProvider != null) {
          return ContainerUtil.concat(myTargets.getValue(), gotoRelatedItemProvider);
        }
        return Collections.emptyList();
      }
    };
    return new RelatedItemLineMarkerInfo<PsiElement>(element, element.getTextRange(), renderer.getIcon(), Pass.UPDATE_OVERRIDEN_MARKERS,
                                                     tooltip == null ? null : new ConstantFunction<PsiElement, String>(tooltip),
                                                     renderer.isNavigateAction() ? renderer : null, renderer.getAlignment(),
                                                     gotoTargets);
  }

  private void checkBuilt() {
    assert myTargets != null : "Must have called .setTargets() before calling create()";
  }

  private MyNavigationGutterIconRenderer createGutterIconRenderer(@NotNull Project project) {
    checkBuilt();
    final SmartPointerManager manager = SmartPointerManager.getInstance(project);

    NotNullLazyValue<List<SmartPsiElementPointer>> pointers = new NotNullLazyValue<List<SmartPsiElementPointer>>() {
      @NotNull
      public List<SmartPsiElementPointer> compute() {
        Set<PsiElement> elements = new THashSet<PsiElement>();
        Collection<? extends T> targets = myTargets.getValue();
        final List<SmartPsiElementPointer> list = new ArrayList<SmartPsiElementPointer>(targets.size());
        for (final T target : targets) {
          for (final PsiElement psiElement : myConverter.fun(target)) {
            if (elements.add(psiElement)) {
              list.add(manager.createSmartPsiElementPointer(psiElement));
            }
          }
        }
        return list;
      }
    };

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

    if (myCellRenderer == null) {
      myCellRenderer = new DefaultPsiElementCellRenderer();
    }

    return new MyNavigationGutterIconRenderer(this, myAlignment, myIcon, myTooltipText, pointers, empty);
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
                                          final String tooltipText,
                                          @NotNull NotNullLazyValue<List<SmartPsiElementPointer>> pointers,
                                          boolean empty) {
      super(builder.myPopupTitle, builder.myEmptyText, builder.myCellRenderer, pointers);
      myAlignment = alignment;
      myIcon = icon;
      myTooltipText = tooltipText;
      myEmpty = empty;
    }

    @Override
    public boolean isNavigateAction() {
      return !myEmpty;
    }

    @NotNull
    public Icon getIcon() {
      return myIcon;
    }

    @Nullable
    public String getTooltipText() {
      return myTooltipText;
    }

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
}
