package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.jsp.JspAction;
import com.intellij.psi.jsp.tagLibrary.JspTagInfo;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.LightweightHint;
import com.intellij.xml.XmlElementDescriptor;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class ShowParameterInfoHandler implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.hint.ShowParameterInfoHandler");

  public void invoke(Project project, Editor editor, PsiFile file) {
    invoke(project, editor, file, -1, null);
  }

  public boolean startInWriteAction() {
    return false;
  }

  public void invoke(final Project project, final Editor editor, PsiFile file, int lbraceOffset, PsiMethod highlightedMethod) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    Lookup lookup = LookupManager.getInstance(project).getActiveLookup();
    if (lookup != null) {
      LookupItem item = lookup.getCurrentItem();
      if (item != null) {
        if (item.getObject() instanceof PsiMethod) {
          showLookupMethodInfo(project, item, editor);
        }
        else if (item.getObject() instanceof XmlElementDescriptor || item.getObject() instanceof JspTagInfo) {
          showEditorHint(new Object[]{item.getObject()}, editor, project);
        }
      }
      return;
    }

    int offset = editor.getCaretModel().getOffset();
    PsiExpressionList list = ParameterInfoController.findArgumentList(file, offset, lbraceOffset);
    if (list != null) {
      showMethodInfo(project, editor, list, highlightedMethod);
    }

    if (file.getManager().getEffectiveLanguageLevel().compareTo(LanguageLevel.JDK_1_5) >= 0) {
      PsiReferenceParameterList refParamList = ParameterInfoController.findParentOfType(file, offset, PsiReferenceParameterList.class);
      if (refParamList != null) {
        showTypeParameterInfo(project, editor, refParamList);
      }
    }

    PsiAnnotation annotation = ParameterInfoController.findParentOfType(file, offset, PsiAnnotation.class);
    if (annotation != null) {
      final PsiElement resolved = annotation.getNameReferenceElement().resolve();
      if (resolved instanceof PsiClass && ((PsiClass)resolved).isAnnotationType()) {
        final PsiAnnotationMethod method = ParameterInfoController.findAnnotationMethod(file, offset);
        showAnnotationMethodsInfo(project, editor, annotation, method);
      }
    }
    final XmlTag tag = ParameterInfoController.findXmlTag(file, offset);
    if (tag != null) {
      showTagInfo(project, editor, tag);
    }

    final JspAction action = ParameterInfoController.findJspAction(file, offset);
    if (action != null) {
      showJspActionInfo(project, editor, action);
    }
  }

  private void showTypeParameterInfo(Project project, Editor editor, PsiReferenceParameterList referenceParameterList) {
    if (!(referenceParameterList.getParent() instanceof PsiJavaCodeReferenceElement)) return;
    final PsiJavaCodeReferenceElement ref = ((PsiJavaCodeReferenceElement)referenceParameterList.getParent());
    final PsiElement psiElement = ref.resolve();
    if (!(psiElement instanceof PsiTypeParameterListOwner)) return;

    final PsiTypeParameter[] typeParams = ((PsiTypeParameterListOwner)psiElement).getTypeParameterList().getTypeParameters();
    if (typeParams.length == 0) return;
    final ParameterInfoComponent component = new ParameterInfoComponent(typeParams, editor);
    component.update();

    showParameterListHint(component, editor, referenceParameterList, project, ",>");
  }

  private void showAnnotationMethodsInfo(final Project project,
                                         final Editor editor,
                                         PsiAnnotation annotation,
                                         PsiMethod highlightedMethod) {
    final PsiElement resolved = annotation.getNameReferenceElement().resolve();
    if (!(resolved instanceof PsiClass) || !((PsiClass)resolved).isAnnotationType()) return;

    final PsiMethod[] methods = ((PsiClass)resolved).getMethods();
    if (methods.length == 0) return;

    final ParameterInfoComponent component = new ParameterInfoComponent(methods, editor);
    component.update();
    component.setHighlightedMethod(highlightedMethod);

    final PsiAnnotationParameterList parameterList = annotation.getParameterList();
    showParameterListHint(component, editor, parameterList, project, ParameterInfoController.DEFAULT_PARAMETER_CLOSE_CHARS);
  }

  private void showParameterListHint(final ParameterInfoComponent component,
                                     final Editor editor,
                                     final PsiElement parameterList, final Project project, final String parameterClosingChars) {
    final LightweightHint hint = new LightweightHint(component);
    final HintManager hintManager = HintManager.getInstance();
    LogicalPosition pos = editor.offsetToLogicalPosition(parameterList.getTextRange().getEndOffset());
    final Point p = chooseBestHintPosition(project, editor, pos.line, pos.column, hint);
    final int offset = parameterList.getTextOffset() + 1;
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (!editor.getComponent().isShowing()) return;
        hintManager.showEditorHint(hint, editor, p,
                                   HintManager.HIDE_BY_ESCAPE | HintManager.HIDE_BY_LOOKUP_ITEM_CHANGE | HintManager.UPDATE_BY_SCROLLING,
                                   0, false);
        new ParameterInfoController(project, editor, offset, hint, 0, parameterClosingChars);
      }
    });
  }

  private void showJspActionInfo(final Project project, final Editor editor, JspAction action) {
    PsiMetaData metaData = action.getMetaData();
    if (!(metaData instanceof JspTagInfo)) {
      if (metaData == null) {
        DaemonCodeAnalyzer.getInstance(project).updateVisibleHighlighters(editor);
      }
      return;
    }

    showXmlOrJspEditorHint(action, editor, metaData, project);
  }

  private void showTagInfo(final Project project, final Editor editor, final XmlTag tag) {
    final XmlElementDescriptor descriptor = tag.getDescriptor();
    if (descriptor == null) {
      DaemonCodeAnalyzer.getInstance(project).updateVisibleHighlighters(editor);
      return;
    }

    showXmlOrJspEditorHint(tag, editor, descriptor, project);
  }

  private void showLookupMethodInfo(final Project project, LookupItem item, final Editor editor) {
    PsiElement[] allElements = LookupManager.getInstance(project).getAllElementsForItem(item);
    PsiMethod[] allMethods = new PsiMethod[allElements.length];
    System.arraycopy(allElements, 0, allMethods, 0, allElements.length);
    showEditorHint(allMethods, editor, project);
  }

  private void showEditorHint(Object[] descriptors, final Editor editor, final Project project) {
    ParameterInfoComponent component = new ParameterInfoComponent(descriptors, editor);
    component.update();

    final LightweightHint hint = new LightweightHint(component);
    final HintManager hintManager = HintManager.getInstance();
    final Point p = chooseBestHintPosition(project, editor, -1, -1, hint);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (!editor.getComponent().isShowing()) return;
        ;

        hintManager.showEditorHint(hint, editor, p,
                                   HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_LOOKUP_ITEM_CHANGE | HintManager.UPDATE_BY_SCROLLING,
                                   0, false);
      }
    });
  }

  private void showXmlOrJspEditorHint(final PsiElement element, final Editor editor, final Object descriptor, final Project project) {
    final int elementStart = element.getTextRange().getStartOffset() + 1;
    if (ParameterInfoController.isAlreadyShown(editor, elementStart)) return;

    final ParameterInfoComponent component = new ParameterInfoComponent(new Object[]{descriptor}, editor);
    component.setCurrentItem(element);
    component.update(); // to have correct preferred size

    final LightweightHint hint = new LightweightHint(component);
    final HintManager hintManager = HintManager.getInstance();

    final int startOffset = element.getTextOffset() + 1;
    LogicalPosition pos = editor.offsetToLogicalPosition(startOffset);
    final Point p = chooseBestHintPosition(project, editor, pos.line, pos.column, hint);

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        hintManager.showEditorHint(hint, editor, p, HintManager.HIDE_BY_ESCAPE | HintManager.UPDATE_BY_SCROLLING, 0, false);
        new ParameterInfoController(project,
                                    editor,
                                    elementStart,
                                    hint,
                                    ParameterInfoController.TYPE_XML_ATTRS);
      }
    });
  }

  private void showMethodInfo(final Project project, final Editor editor, PsiExpressionList list, PsiMethod highlightedMethod) {
    CandidateInfo[] candidates = getMethods(list);
    if (candidates.length == 0) {
      DaemonCodeAnalyzer.getInstance(project).updateVisibleHighlighters(editor);
      return;
    }

    String listText = list.getText();
    final boolean isMultiline = listText.indexOf('\n') >= 0 || listText.indexOf('\r') >= 0;

    final int listOffset = list.getTextRange().getStartOffset();
    if (ParameterInfoController.isAlreadyShown(editor, listOffset)) return;
    int startOffset = listOffset + 1;
    final LogicalPosition pos = editor.offsetToLogicalPosition(startOffset);
    final ParameterInfoComponent component = new ParameterInfoComponent(candidates, editor);
    if (candidates.length > 1) {
      component.setHighlightedMethod(highlightedMethod);
    }
    component.update(); // to have correct preferred size
    final LightweightHint hint = new LightweightHint(component);
    final HintManager hintManager = HintManager.getInstance();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        Point p;
        if (!isMultiline) {
          p = chooseBestHintPosition(project, editor, pos.line, pos.column, hint);
        }
        else {
          p = hintManager.getHintPosition(hint, editor, pos, HintManager.ABOVE);
          Dimension hintSize = hint.getComponent().getPreferredSize();
          JComponent editorComponent = editor.getComponent();
          JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();
          p.x = Math.min(p.x, layeredPane.getWidth() - hintSize.width);
          p.x = Math.max(p.x, 0);
        }
        hintManager.showEditorHint(hint, editor, p, HintManager.HIDE_BY_ESCAPE | HintManager.UPDATE_BY_SCROLLING, 0, false);
        new ParameterInfoController(project, editor, listOffset, hint, 0);
      }
    });
  }

  /**
   * @return Point in layered pane coordinate system
   */
  private Point chooseBestHintPosition(Project project, Editor editor, int line, int col, LightweightHint hint) {
    HintManager hintManager = HintManager.getInstance();
    Dimension hintSize = hint.getComponent().getPreferredSize();
    JComponent editorComponent = editor.getComponent();
    JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();

    Point p1;
    Point p2;
    boolean isLookupShown = LookupManager.getInstance(project).getActiveLookup() != null;
    if (isLookupShown) {
      p1 = hintManager.getHintPosition(hint, editor, HintManager.UNDER);
      p2 = hintManager.getHintPosition(hint, editor, HintManager.ABOVE);
    }
    else {
      LogicalPosition pos = new LogicalPosition(line, col);
      p1 = hintManager.getHintPosition(hint, editor, pos, HintManager.UNDER);
      p2 = hintManager.getHintPosition(hint, editor, pos, HintManager.ABOVE);
    }

    p1.x = Math.min(p1.x, layeredPane.getWidth() - hintSize.width);
    p1.x = Math.max(p1.x, 0);
    p2.x = Math.min(p2.x, layeredPane.getWidth() - hintSize.width);
    p2.x = Math.max(p2.x, 0);
    boolean p1Ok = p1.y + hintSize.height < layeredPane.getHeight();
    boolean p2Ok = p2.y >= 0;

    if (isLookupShown) {
      if (p2Ok) return p2;
      if (p1Ok) return p1;
    }
    else {
      if (p1Ok) return p1;
      if (p2Ok) return p2;
    }

    int underSpace = layeredPane.getHeight() - p1.y;
    int aboveSpace = p2.y;
    Point p = aboveSpace > underSpace ? new Point(p2.x, 0) : p1;
    return p;
  }

  private CandidateInfo[] getMethods(PsiExpressionList argList) {
    final PsiCall call = ParameterInfoController.getCall(argList);
    PsiResolveHelper helper = argList.getManager().getResolveHelper();
    if (call instanceof PsiCallExpression) {
      ArrayList<CandidateInfo> result;
      CandidateInfo[] candidates = helper.getReferencedMethodCandidates((PsiCallExpression)call, true);
      result = new ArrayList<CandidateInfo>();
      if (!(argList.getParent() instanceof PsiAnonymousClass)) {
        for (int i = 0; i < candidates.length; i++) {
          CandidateInfo candidate = candidates[i];
          if (candidate.isStaticsScopeCorrect() && candidate.isAccessible()) result.add(candidate);
        }
      }
      else {
        PsiClass aClass = (PsiAnonymousClass)argList.getParent();
        for (int i = 0; i < candidates.length; i++) {
          CandidateInfo candidate = candidates[i];
          if (candidate.isStaticsScopeCorrect() && helper.isAccessible(((PsiMethod)candidate.getElement()), argList, aClass)) result.add(candidate);
        }
      }
      return result.toArray(new CandidateInfo[result.size()]);
    }
    else {
      LOG.assertTrue(call instanceof PsiEnumConstant);
      //We are inside our own enum, no isAccessible check needed
      PsiMethod[] constructors = ((PsiEnumConstant)call).getContainingClass().getConstructors();
      CandidateInfo[] result = new CandidateInfo[constructors.length];
      for (int i = 0; i < constructors.length; i++) {
        result[i] = new CandidateInfo(constructors[i], PsiSubstitutor.EMPTY);
      }
      return result;
    }
  }
}

