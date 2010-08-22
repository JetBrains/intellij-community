/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.navigation;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
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
  private final NotNullFunction<T,Collection<? extends PsiElement>> myConvertor;

  private NotNullLazyValue<Collection<? extends T>> myTargets;
  private boolean myLazy;
  private String myTooltipText;
  private String myPopupTitle;
  private String myEmptyText;
  private String myTooltipTitle;
  private GutterIconRenderer.Alignment myAlignment = GutterIconRenderer.Alignment.CENTER;
  private PsiElementListCellRenderer myCellRenderer;
  private NullableFunction<T,String> myNamer = (NullableFunction<T, String>)ElementPresentationManager.NAMER;
  public static final NotNullFunction<DomElement,Collection<? extends PsiElement>> DEFAULT_DOM_CONVERTOR = new NotNullFunction<DomElement, Collection<? extends PsiElement>>() {
    @NotNull
    public Collection<? extends PsiElement> fun(final DomElement o) {
      return ContainerUtil.createMaybeSingletonList(o.getXmlElement());
    }
  };

  protected NavigationGutterIconBuilder(@NotNull final Icon icon, NotNullFunction<T,Collection<? extends PsiElement>> convertor) {
    myIcon = icon;
    myConvertor = convertor;
  }

  public static NavigationGutterIconBuilder<PsiElement> create(@NotNull final Icon icon) {
    return create(icon, DEFAULT_PSI_CONVERTOR);
  }

  public static <T> NavigationGutterIconBuilder<T> create(@NotNull final Icon icon, NotNullFunction<T, Collection<? extends PsiElement>> convertor) {
    return new NavigationGutterIconBuilder<T>(icon, convertor);
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

  private Annotation doInstall(final Annotation annotation, final Project project) {
    final MyNavigationGutterIconRenderer renderer = createGutterIconRenderer(project);
    annotation.setGutterIconRenderer(renderer);
    annotation.setNeedsUpdateOnTyping(false);
    return annotation;
  }

  public LineMarkerInfo createLineMarkerInfo(PsiElement element) {
    final MyNavigationGutterIconRenderer renderer = createGutterIconRenderer(element.getProject());
    final String tooltip = renderer.getTooltipText();
    return new LineMarkerInfo<PsiElement>(element,
                                          element.getTextRange(),
                                          renderer.getIcon(),
                                          Pass.UPDATE_OVERRIDEN_MARKERS,
                                          tooltip == null ? null : new ConstantFunction<PsiElement, String>(tooltip),
                                          renderer,
                                          renderer.getAlignment());
  }

  private MyNavigationGutterIconRenderer createGutterIconRenderer(final Project project) {
    final SmartPointerManager manager = SmartPointerManager.getInstance(project);

    NotNullLazyValue<List<SmartPsiElementPointer>> pointers = new NotNullLazyValue<List<SmartPsiElementPointer>>() {
      @NotNull
      public List<SmartPsiElementPointer> compute() {
        Set<PsiElement> elements = new THashSet<PsiElement>();
        final ArrayList<SmartPsiElementPointer> list = new ArrayList<SmartPsiElementPointer>();
        for (final T target : myTargets.getValue()) {
          for (final PsiElement psiElement : myConvertor.fun(target)) {
            if (elements.add(psiElement)) {
              list.add(manager.createSmartPsiElementPointer(psiElement));
            }
          }
        }
        return list;
      }
    };

    if (!myLazy) {
      pointers.getValue();
    }

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

    final MyNavigationGutterIconRenderer renderer = new MyNavigationGutterIconRenderer(this, myAlignment, myIcon, myTooltipText, pointers);
    return renderer;
  }

  private static class MyNavigationGutterIconRenderer extends NavigationGutterIconRenderer {
    private final Alignment myAlignment;
    private final Icon myIcon;
    private final String myTooltipText;

    public MyNavigationGutterIconRenderer(final NavigationGutterIconBuilder builder, final Alignment alignment, final Icon icon, final String tooltipText, final NotNullLazyValue<List<SmartPsiElementPointer>> pointers) {
      super(builder.myPopupTitle, builder.myEmptyText, builder.myCellRenderer, pointers);
      myAlignment = alignment;
      myIcon = icon;
      myTooltipText = tooltipText;
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
      if (o == null || getClass() != o.getClass()) return false;
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
