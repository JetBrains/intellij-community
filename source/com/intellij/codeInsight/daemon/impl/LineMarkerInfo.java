
package com.intellij.codeInsight.daemon.impl;

import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiSuperMethodUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;
import java.text.MessageFormat;
import java.util.*;

public class LineMarkerInfo {
  public static final LineMarkerInfo[] EMPTY_ARRAY = new LineMarkerInfo[0];

  public static final int OVERRIDING_METHOD = 1;
  public static final int OVERRIDEN_METHOD = 2;
  public static final int METHOD_SEPARATOR = 5;
  public static final int SUBCLASSED_CLASS = 6;
  public static final int BOUND_CLASS_OR_FIELD = 7;

  public final int type;
  private Icon myIcon;
  public final WeakReference<PsiElement> elementRef;
  public final int startOffset;
  public TextAttributes attributes;
  public Color separatorColor;
  public SeparatorPlacement separatorPlacement;
  public RangeHighlighter highlighter;

  public LineMarkerInfo(int type, PsiElement element, int startOffset, Icon icon) {
    this.type = type;
    myIcon = icon;
    elementRef = new WeakReference<PsiElement>(element);
    this.startOffset = startOffset;
  }

  public GutterIconRenderer createGutterRenderer() {
    if (myIcon == null) return null;
    return new GutterIconRenderer() {
      public Icon getIcon() {
        return myIcon;
      }

      public AnAction getClickAction() {
        return new NavigateAction();
      }

      public boolean isNavigateAction() {
        return true;
      }

      public String getTooltipText() {
        return getLineMarkerTooltip();
      }

      public GutterIconRenderer.Alignment getAlignment() {
        boolean isImplements = type == OVERRIDING_METHOD;
        return isImplements ? GutterIconRenderer.Alignment.LEFT : GutterIconRenderer.Alignment.RIGHT;
      }
    };
  }

  private String getLineMarkerTooltip() {
    PsiElement element = elementRef.get();
    if (element == null || !element.isValid()) return null;
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      return getMethodTooltip(method);
    }
    else if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass)element;
      return getClassTooltip(aClass);
    }
    else if (element instanceof PsiField) {
      PsiField psiField = (PsiField)element;
      return getFieldTooltip(psiField);
    }
    return null;
  }

  private String getMethodTooltip(PsiMethod method) {
    if (type == OVERRIDING_METHOD){
      PsiMethod[] superMethods = PsiSuperMethodUtil.findSuperMethods(method, false);
      if (superMethods.length == 0) return null;

      PsiMethod superMethod = superMethods[0];
      StringBuffer format = new StringBuffer();
      boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
      boolean isSuperAbstract = superMethod.hasModifierProperty(PsiModifier.ABSTRACT);
      if (isSuperAbstract && !isAbstract){
        format.append("Implements");
      }
      else{
        format.append("Overrides");
      }
      format.append(" method");
      if (!superMethod.getSignature(PsiSubstitutor.EMPTY).equals(method.getSignature(PsiSubstitutor.EMPTY))) {
        format.append(" ''{0}''");
      }
      format.append(" in ''{1}''");
      return composeText(superMethods, "", format.toString());
    }
    else if (type == OVERRIDEN_METHOD){
      PsiManager manager = method.getManager();
      PsiSearchHelper helper = manager.getSearchHelper();
      PsiElementProcessor.CollectElementsWithLimit<PsiMethod> processor = new PsiElementProcessor.CollectElementsWithLimit<PsiMethod>(5);
      helper.processOverridingMethods(processor, method, GlobalSearchScope.allScope(manager.getProject()), true);

      boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);

      if (processor.isOverflow()){
        return isAbstract ? "Has implementations" : "Is overridden in subclasses";
      }

      PsiMethod[] overridings = processor.toArray(new PsiMethod[processor.getCollection().size()]);
      if (overridings.length == 0) return null;

      Comparator comparator = new MethodCellRenderer(false).getComparator();
      Arrays.sort(overridings, comparator);

      String start = isAbstract ? "Is implemented in <br>" : "Is overridden in <br>";
      String pattern = "&nbsp;&nbsp;&nbsp;&nbsp;{1}";
      return composeText(overridings, start, pattern);
    }
    else{
      return null;
    }
  }

  private String getFieldTooltip(PsiField psiField) {
    if (type == BOUND_CLASS_OR_FIELD) {
      PsiSearchHelper helper = psiField.getManager().getSearchHelper();
      PsiClass aClass = psiField.getContainingClass();
      if (aClass != null && aClass.getQualifiedName() != null) {
        PsiFile[] formFiles = helper.findFormsBoundToClass(aClass.getQualifiedName());
        return composeText(formFiles, "UI is bound in<br>", "&nbsp;&nbsp;&nbsp;&nbsp;{0}");
      }
    }
    return null;
  }

  private String getClassTooltip(PsiClass aClass) {
    PsiManager manager = aClass.getManager();
    PsiSearchHelper helper = manager.getSearchHelper();
    if (type == SUBCLASSED_CLASS) {
      PsiElementProcessor.CollectElementsWithLimit<PsiClass> processor = new PsiElementProcessor.CollectElementsWithLimit<PsiClass>(5);
      GlobalSearchScope scope = GlobalSearchScope.allScope(manager.getProject());
      helper.processInheritors(processor, aClass, scope, false);

      if (processor.isOverflow()) {
        return aClass.isInterface() ? "Has implementations" : "Has subclasses";
      }

      PsiClass[] subclasses = processor.toArray(new PsiClass[processor.getCollection().size()]);
      if (subclasses.length == 0) return null;

      Comparator comparator = new PsiClassListCellRenderer().getComparator();
      Arrays.sort(subclasses, comparator);

      String start = aClass.isInterface() ? "Is implemented by<br>" : "Is subclassed by<br>";
      String pattern = "&nbsp;&nbsp;&nbsp;&nbsp;{0}";
      return composeText(subclasses, start, pattern);
    }
    else if (type == BOUND_CLASS_OR_FIELD) {
      if (aClass.getQualifiedName() != null) {
        PsiFile[] formFiles = helper.findFormsBoundToClass(aClass.getQualifiedName());
        return composeText(formFiles, "UI is bound in<br>", "&nbsp;&nbsp;&nbsp;&nbsp;{0}");
      }
    }
    return null;
  }

  private class NavigateAction extends AnAction {
    public void actionPerformed(AnActionEvent e) {
      MouseEvent mousEvent = (MouseEvent) e.getInputEvent();
      LineMarkerNavigator.browse(mousEvent, LineMarkerInfo.this);
    }
  }

  private static String composeText(PsiElement[] elements, String start, String formatPattern) {
    StringBuffer result = new StringBuffer();
    result.append("<html><body>");
    result.append(start);
    Set<String> names = new LinkedHashSet<String>();
    for (PsiElement element : elements) {
      String descr = "";
      if (element instanceof PsiClass) {
        String className = ClassPresentationUtil.getNameForClass((PsiClass)element, true);
        descr = MessageFormat.format(formatPattern, new Object[]{className});
      }
      else if (element instanceof PsiMethod) {
        String methodName = ((PsiMethod)element).getName();
        String className = ClassPresentationUtil.getNameForClass(((PsiMethod)element).getContainingClass(), true);
        descr = MessageFormat.format(formatPattern, new Object[]{methodName, className});
      }
      else if (element instanceof PsiFile) {
        descr = MessageFormat.format(formatPattern, new Object[]{((PsiFile)element).getName()});
      }
      names.add(descr);
    }

    String sep = "";
    for (String name : names) {
      result.append(sep);
      sep = "<br>";
      result.append(name);
    }

    result.append("</body></html>");
    return result.toString();
  }
}