package com.intellij.codeInsight.highlighting;

import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowAnalyzer;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.controlFlow.LocalsOrMyInstanceFieldsControlFlowPolicy;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.SearchScopeCache;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.ListPopup;
import com.intellij.util.containers.IntArrayList;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class HighlightUsagesHandler extends HighlightHandlerBase {
  /*
  private static final Object HIGHLIGHTERS_IN_EDITOR_VIEW_KEY = Key.create("HighlightUsagesHandler.HIGHLIGHTERS_IN_EDITOR_VIEW_KEY");
  private static final Object MARKERS_IN_EDITOR_VIEW_KEY = Key.create("HighlightUsagesHandler.MARKERS_IN_EDITOR_VIEW_KEY");
  */

  public void invoke(Project project, Editor editor, PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    SelectionModel selectionModel = editor.getSelectionModel();
    if (file == null && !selectionModel.hasSelection()) {
      selectionModel.selectWordAtCaret(false);
    }
    if (file == null || selectionModel.hasSelection()) {
      doRangeHighlighting(editor, project);
      return;
    }

    PsiElement target = getTargetElement(editor);

    if (target == null) {
      if (file.findElementAt(editor.getCaretModel().getOffset()) instanceof PsiWhiteSpace) return;
      selectionModel.selectWordAtCaret(false);
      String selection = selectionModel.getSelectedText();
      for (int i = 0; i < selection.length(); i++) {
        if (!Character.isJavaIdentifierPart(selection.charAt(i))) {
          selectionModel.removeSelection();
          return;
        }
      }

      doRangeHighlighting(editor, project);
      selectionModel.removeSelection();
      return;
    }

    createHighlightAction(project, file, target, editor).run();
  }

  protected PsiElement getTargetElement(Editor editor) {
    PsiElement target = TargetElementUtil.findTargetElement(editor,
                                                            TargetElementUtil.ELEMENT_NAME_ACCEPTED |
                                                            TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED |
                                                            TargetElementUtil.NEW_AS_CONSTRUCTOR |
                                                            TargetElementUtil.LOOKUP_ITEM_ACCEPTED |
                                                            TargetElementUtil.TRY_ACCEPTED |
                                                            TargetElementUtil.CATCH_ACCEPTED |
                                                            TargetElementUtil.THROWS_ACCEPTED |
                                                            TargetElementUtil.THROW_ACCEPTED |
                                                            TargetElementUtil.RETURN_ACCEPTED);

    if (target instanceof PsiCompiledElement) target = ((PsiCompiledElement)target).getMirror();
    return target;
  }

  protected void doRangeHighlighting(Editor editor, Project project) {
    if (!editor.getSelectionModel().hasSelection()) return;

    String text = editor.getSelectionModel().getSelectedText();
    FindManager findManager = FindManager.getInstance(project);
    FindModel model = new FindModel();
    model.setCaseSensitive(true);
    model.setFromCursor(false);
    model.setStringToFind(text);
    model.setSearchHighlighters(true);
    int offset = 0;
    FindResult result;
    HighlightManager highlighter = HighlightManager.getInstance(project);
    EditorColorsManager colorManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorManager.getGlobalScheme().getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES);
    int count = 0;
    while (true) {
      result =
      findManager.findString(editor.getDocument().getCharsSequence(), offset, model);
      if (result == null || !result.isStringFound()) break;
      highlighter.addRangeHighlight(editor, result.getStartOffset(), result.getEndOffset(),
                                    attributes, false, null);
      offset = result.getEndOffset();
      count++;
    }
    findManager.setFindWasPerformed();
    findManager.setFindNextModel(model);

    WindowManager.getInstance().getStatusBar(project).setInfo("Highlighted " + count + " occurencies of \"" + model.getStringToFind() +
                                                              "\" (press Escape to remove the highlighting)");
  }

  private static final Runnable EMPTY_HIGHLIGHT_RUNNABLE = EmptyRunnable.getInstance();

  private class DoHighlightExitPointsRunnable implements Runnable {
    private Project myProject;
    private Editor myEditor;
    private PsiElement[] myExitStatements;

    public DoHighlightExitPointsRunnable(Project project, Editor editor, PsiElement[] exitStatements) {
      myProject = project;
      myEditor = editor;
      myExitStatements = exitStatements;
    }

    public void run() {
      TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      doHighlightElements(HighlightManager.getInstance(myProject), myEditor, myExitStatements, attributes);

      setupFindModel(myProject);
      StringBuffer buffer = new StringBuffer();
      buffer.append(myExitStatements.length);
      buffer.append(" exit point");
      if (myExitStatements.length > 1) buffer.append("s");
      buffer.append(" highlighted (press Escape to remove the highlighting)");
      WindowManager.getInstance().getStatusBar(myProject).setInfo(buffer.toString());
    }
  }

  private class DoHighlightRunnable implements Runnable {
    private PsiReference[] myRefs;
    private Project myProject;
    private PsiElement myTarget;
    private Editor myEditor;
    private PsiFile myFile;

    public DoHighlightRunnable(PsiReference[] refs, Project project, PsiElement target, Editor editor, PsiFile file) {
      myRefs = refs;
      myProject = project;
      myTarget = target;
      myEditor = editor;
      myFile = file;
    }

    public void run() {
      highlightReferences(myProject, myTarget, myRefs, myEditor, myFile);

      setStatusText(myTarget, myRefs.length, myProject);

      FindUsagesOptions options = new FindUsagesOptions(myProject, SearchScopeCache.getInstance(myProject));
      options.isUsages = true;
      options.isReadAccess = true;
      options.isWriteAccess = true;
      //FindManager.getInstance(myProject).setLastSearchOperation(myTarget, options);
    }
  }

  private class ChooseExceptionClassAndDoHighlightRunnable implements Runnable {
    private PsiClass[] myExceptionClasses;
    private PsiElement myHighlightInPlace;
    private Project myProject;
    private PsiElement myTarget;
    private Editor myEditor;
    private PsiFile myFile;
    private JList myList;
    private TypeFilter myTypeFilter = ANY_TYPE;

    public ChooseExceptionClassAndDoHighlightRunnable(PsiClassType[] exceptions, PsiElement highlightInPlace, Project project,
                                                      PsiElement target, Editor editor, PsiFile file) {
      List<PsiClass> classes = new ArrayList<PsiClass>();
      for (int i = 0; i < exceptions.length; i++) {
        PsiClass exception = exceptions[i].resolve();
        if (exception != null) classes.add(exception);
      }
      myExceptionClasses = classes.toArray(new PsiClass[classes.size()]);
      myHighlightInPlace = highlightInPlace;
      myProject = project;
      myTarget = target;
      myEditor = editor;
      myFile = file;
    }

    public void setTypeFilter(TypeFilter typeFilter) {
      myTypeFilter = typeFilter;
    }

    public void run() {
      final PsiElementFactory factory = PsiManager.getInstance(myProject).getElementFactory();
      if (myExceptionClasses.length == 1) {
        ArrayList<PsiReference> refs = new ArrayList<PsiReference>();
        findExceptionThrownPlaces(refs, factory.createType(myExceptionClasses[0]), myHighlightInPlace, myTypeFilter);
        new DoHighlightRunnable(refs.toArray(new PsiReference[refs.size()]), myProject, myTarget,
                                myEditor, myFile).run();
      }
      else if (myExceptionClasses.length > 0) {
        PsiElementListCellRenderer renderer = new PsiClassListCellRenderer();

        Arrays.sort(myExceptionClasses, renderer.getComparator());

        Vector<Object> model = new Vector<Object>(Arrays.asList(myExceptionClasses));
        model.insertElementAt("All listed", 0);

        myList = new JList(model);
        myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        myList.setCellRenderer(renderer);

        renderer.installSpeedSearch(myList);

        final Runnable callback = new Runnable() {
          public void run() {
            int idx = myList.getSelectedIndex();
            if (idx < 0) return;
            ArrayList<PsiReference> refs = new ArrayList<PsiReference>();
            if (idx > 0) {
              findExceptionThrownPlaces(refs,
                                        factory.createType(myExceptionClasses[idx - 1]),
                                        myHighlightInPlace,
                                        myTypeFilter);
            }
            else {
              for (int i = 0; i < myExceptionClasses.length; i++) {
                findExceptionThrownPlaces(refs,
                                          factory.createType(myExceptionClasses[i]),
                                          myHighlightInPlace,
                                          myTypeFilter);
              }
            }


            new DoHighlightRunnable(refs.toArray(new PsiReference[refs.size()]), myProject, myTarget,
                                    myEditor, myFile).run();
          }
        };

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            String title = " Choose Exception Classes to Highlight";
            ListPopup listPopup = new ListPopup(title, myList, callback, myProject);
            LogicalPosition caretPosition = myEditor.getCaretModel().getLogicalPosition();
            Point caretLocation = myEditor.logicalPositionToXY(caretPosition);
            int x = caretLocation.x;
            int y = caretLocation.y;
            Point location = myEditor.getContentComponent().getLocationOnScreen();
            x += location.x;
            y += location.y;
            listPopup.show(x, y);
          }
        });
      }
    }
  }

  public boolean startInWriteAction() {
    return false;
  }

  protected Runnable createHighlightAction(final Project project, PsiFile file, PsiElement target, final Editor editor) {
    if (file instanceof PsiCompiledElement) file = (PsiFile)((PsiCompiledElement)file).getMirror();

    if (target instanceof PsiKeyword) {
      if (PsiKeyword.TRY.equals(target.getText())) {
        PsiElement parent = target.getParent();
        if (!(parent instanceof PsiTryStatement)) {
          return EMPTY_HIGHLIGHT_RUNNABLE;
        }
        PsiTryStatement tryStatement = (PsiTryStatement)parent;

        final PsiClassType[] psiClassTypes = ExceptionUtil.collectUnhandledExceptions(tryStatement.getTryBlock(),
                                                                                      tryStatement.getTryBlock());

        return createChoosingRunnable(project, psiClassTypes, tryStatement.getTryBlock(), target, editor, file,
                                      ANY_TYPE);
      }

      if (PsiKeyword.CATCH.equals(target.getText())) {
        PsiElement parent = target.getParent();
        if (!(parent instanceof PsiCatchSection)) {
          return EMPTY_HIGHLIGHT_RUNNABLE;
        }
        PsiTryStatement tryStatement = ((PsiCatchSection)parent).getTryStatement();

        PsiParameter param = ( (PsiCatchSection)parent).getParameter();
        if (param == null) return EMPTY_HIGHLIGHT_RUNNABLE;

        final PsiParameter[] catchBlockParameters = tryStatement.getCatchBlockParameters();

        final PsiClassType[] allThrownExceptions = ExceptionUtil.collectUnhandledExceptions(tryStatement.getTryBlock(),
                                                                                            tryStatement.getTryBlock());

        final PsiElement param1 = param;
        TypeFilter filter = new TypeFilter() {
          public boolean accept(PsiType type) {
            for (int j = 0; j < catchBlockParameters.length; j++) {
              PsiParameter parameter = catchBlockParameters[j];
              if (parameter != param1) {
                if (parameter.getType().isAssignableFrom(type)) return false;
              }
              else {
                return parameter.getType().isAssignableFrom(type);
              }
            }
            return false;
          }
        };

        ArrayList<PsiClassType> filtered = new ArrayList<PsiClassType>();
        for (int i = 0; i < allThrownExceptions.length; i++) {
          PsiClassType type = allThrownExceptions[i];
          if (filter.accept(type)) filtered.add(type);
        }

        return createChoosingRunnable(project, filtered.toArray(new PsiClassType[filtered.size()]),
                                      tryStatement.getTryBlock(), target, editor, file, filter);
      }

      if (PsiKeyword.THROWS.equals(target.getText())) {
        PsiElement parent = target.getParent().getParent();
        if (!(parent instanceof PsiMethod)) return EMPTY_HIGHLIGHT_RUNNABLE;
        PsiMethod method = (PsiMethod)parent;
        if (method.getBody() == null) return EMPTY_HIGHLIGHT_RUNNABLE;

        final PsiClassType[] psiClassTypes = ExceptionUtil.collectUnhandledExceptions(method.getBody(),
                                                                                      method.getBody());

        return createChoosingRunnable(project, psiClassTypes, method.getBody(), target, editor, file, ANY_TYPE);
      }

      if (PsiKeyword.RETURN.equals(target.getText()) || PsiKeyword.THROW.equals(target.getText())) {
        PsiElement parent = target.getParent();
        if (!(parent instanceof PsiReturnStatement) && !(parent instanceof PsiThrowStatement)) return EMPTY_HIGHLIGHT_RUNNABLE;

        PsiMethod method = PsiTreeUtil.getParentOfType(target, PsiMethod.class);
        if (method == null) return EMPTY_HIGHLIGHT_RUNNABLE;

        PsiCodeBlock body = method.getBody();
        try {
          ControlFlow flow = new ControlFlowAnalyzer(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance()).buildControlFlow();

          List<PsiStatement> exitStatements = new ArrayList<PsiStatement>();
          ControlFlowUtil.findExitPointsAndStatements(flow, flow.getStartOffset(body), flow.getEndOffset(body), new IntArrayList(),
                                                      exitStatements,
                                                      new Class[]{PsiReturnStatement.class, PsiBreakStatement.class,
                                                                  PsiContinueStatement.class, PsiThrowStatement.class,
                                                                  PsiExpressionStatement.class});

          if (!exitStatements.contains(parent)) return EMPTY_HIGHLIGHT_RUNNABLE;

          return new DoHighlightExitPointsRunnable(project, editor, exitStatements.toArray(new PsiElement[exitStatements.size()]));
        }
        catch (ControlFlowAnalyzer.AnalysisCanceledException e) {
          return EMPTY_HIGHLIGHT_RUNNABLE;
        }
      }
    }

    PsiSearchHelper helper = PsiManager.getInstance(project).getSearchHelper();
    SearchScope searchScope = new LocalSearchScope(file);
    PsiReference[] refs = PsiReference.EMPTY_ARRAY;
    if (file instanceof PsiJavaFile || file instanceof JspFile || file instanceof XmlFile) {
      if (target instanceof PsiMethod) {
        refs = helper.findReferencesIncludingOverriding((PsiMethod)target, searchScope, true);
      }
      else {
        refs = helper.findReferences(target, searchScope, false);
      }
    }

    return new DoHighlightRunnable(refs, project, target, editor, file);
  }

  private Runnable createChoosingRunnable(Project project, final PsiClassType[] psiClassTypes,
                                          PsiElement place, PsiElement target, Editor editor,
                                          PsiFile file, TypeFilter typeFilter) {
    if (psiClassTypes == null || psiClassTypes.length == 0) return EMPTY_HIGHLIGHT_RUNNABLE;

    final ChooseExceptionClassAndDoHighlightRunnable highlightRunnable =
      new ChooseExceptionClassAndDoHighlightRunnable(psiClassTypes, place, project, target, editor, file);
    highlightRunnable.setTypeFilter(typeFilter);
    return highlightRunnable;
  }

  private interface TypeFilter {
    boolean accept(PsiType type);
  }

  private static final TypeFilter ANY_TYPE = new TypeFilter() {
    public boolean accept(PsiType type) {
      return true;
    }
  };

  private void findExceptionThrownPlaces(final List<PsiReference> refs, final PsiType type, final PsiElement block,
                                         final TypeFilter typeFilter) {
    if (type instanceof PsiClassType) {
      block.accept(new PsiRecursiveElementVisitor() {
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          visitElement(expression);
        }

        public void visitThrowStatement(PsiThrowStatement statement) {
          super.visitThrowStatement(statement);
          PsiClassType[] exceptionTypes = ExceptionUtil.getUnhandledExceptions(statement, block);
          if (exceptionTypes != null) {
            for (int i = 0; i < exceptionTypes.length; i++) {
              final PsiType actualType = exceptionTypes[i];
              if (type.isAssignableFrom(actualType) && typeFilter.accept(actualType)) {
                if (!(statement.getException() instanceof PsiNewExpression)) continue;
                PsiJavaCodeReferenceElement ref = ((PsiNewExpression)statement.getException()).getClassReference();
                if (refs.contains(ref)) continue;
                refs.add(ref);
              }
            }
          }
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
          super.visitMethodCallExpression(expression);
          if (refs.contains(expression.getMethodExpression().getReference())) return;
          PsiClassType[] exceptionTypes = ExceptionUtil.getUnhandledExceptions(expression, block);
          for (int i = 0; i < exceptionTypes.length; i++) {
            final PsiType actualType = exceptionTypes[i];
            if (type.isAssignableFrom(actualType) && typeFilter.accept(actualType)) {
              refs.add(expression.getMethodExpression().getReference());
            }
          }
        }

        public void visitNewExpression(PsiNewExpression expression) {
          super.visitNewExpression(expression);
          if (refs.contains(expression.getClassReference())) return;
          PsiClassType[] exceptionTypes = ExceptionUtil.getUnhandledExceptions(expression, block);
          for (int i = 0; i < exceptionTypes.length; i++) {
            PsiClassType actualType = exceptionTypes[i];
            if (type.isAssignableFrom(actualType) && typeFilter.accept(actualType)) {
              refs.add(expression.getClassReference());
            }
          }
        }
      });
    }
  }

  private void highlightReferences(Project project,
                                   PsiElement element,
                                   PsiReference[] refs,
                                   Editor editor,
                                   PsiFile file) {

    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager manager = EditorColorsManager.getInstance();
    TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    TextAttributes writeAttributes = manager.getGlobalScheme().getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES);

    setupFindModel(project);

    if (element instanceof PsiVariable) {
      List<PsiReference> readRefs = new ArrayList<PsiReference>();
      List<PsiReference> writeRefs = new ArrayList<PsiReference>();
      for (int i = 0; i < refs.length; i++) {
        PsiReference ref = refs[i];
        PsiElement refElement = ref.getElement();
        if (refElement instanceof PsiReferenceExpression && PsiUtil.isAccessedForWriting((PsiExpression)refElement)) {
          writeRefs.add(ref);
        }
        else {
          readRefs.add(ref);
        }
      }
      doHighlightRefs(highlightManager, editor, readRefs.toArray(new PsiReference[readRefs.size()]), attributes);
      doHighlightRefs(highlightManager, editor, writeRefs.toArray(new PsiReference[writeRefs.size()]), writeAttributes);
    }
    else {
      doHighlightRefs(highlightManager, editor, refs, attributes);
    }

    PsiIdentifier identifier = getNameIdentifier(element);
    if (identifier != null && PsiUtil.isUnderPsiRoot(file, identifier)) {
      TextAttributes nameAttributes = attributes;
      if (element instanceof PsiVariable && ((PsiVariable)element).getInitializer() != null) {
        nameAttributes = writeAttributes;
      }
      doHighlightElements(highlightManager, editor, new PsiElement[]{identifier}, nameAttributes);
    }
    else if (element instanceof PsiKeyword) { //try, catch, throws.
      doHighlightElements(highlightManager, editor, new PsiElement[]{element}, attributes);
    }
  }

  private void doHighlightElements(HighlightManager highlightManager,
                                   Editor editor,
                                   PsiElement[] elements,
                                   TextAttributes attributes) {
    ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    Document document = editor.getDocument();
    highlightManager.addOccurrenceHighlights(editor, elements, attributes, false, highlighters);

    int idx = 0;
    for (Iterator<RangeHighlighter> iterator = highlighters.iterator(); iterator.hasNext(); idx++) {
      RangeHighlighter highlighter = iterator.next();
      int offset = elements[idx].getTextRange().getStartOffset();
      setLineTextErrorStripeTooltip(document, offset, highlighter);
    }
  }

  private void doHighlightRefs(HighlightManager highlightManager,
                               Editor editor,
                               PsiReference[] refs,
                               TextAttributes attributes) {
    ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    Document document = editor.getDocument();
    highlightManager.addOccurrenceHighlights(editor, refs, attributes, false, highlighters);
    int idx = 0;
    for (Iterator<RangeHighlighter> iterator = highlighters.iterator(); iterator.hasNext(); idx++) {
      RangeHighlighter highlighter = iterator.next();
      int offset = refs[idx].getElement().getTextRange().getStartOffset() + refs[idx].getRangeInElement().getStartOffset();
      setLineTextErrorStripeTooltip(document, offset, highlighter);
    }
  }

  private PsiIdentifier getNameIdentifier(PsiElement element) {
    if (element instanceof PsiClass) {
      return ((PsiClass)element).getNameIdentifier();
    }
    if (element instanceof PsiMethod) {
      return ((PsiMethod)element).getNameIdentifier();
    }
    if (element instanceof PsiVariable) {
      return ((PsiVariable)element).getNameIdentifier();
    }
    if (element instanceof PsiPointcutDef) {
      return ((PsiPointcutDef)element).getNameIdentifier();
    }

    return null;
  }

  private void setStatusText(PsiElement element, int refCount, Project project) {
    String elementName = null;
    if (element instanceof PsiClass) {
      elementName = ((PsiClass)element).getQualifiedName();
      if (elementName == null) {
        elementName = ((PsiClass)element).getName();
      }
      elementName = (((PsiClass)element).isInterface() ? "interface " : "class ") + elementName;
    }
    else if (element instanceof PsiMethod) {
      elementName = PsiFormatUtil.formatMethod((PsiMethod)element,
                                               PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS |
                                                                     PsiFormatUtil.SHOW_CONTAINING_CLASS,
                                               PsiFormatUtil.SHOW_TYPE);
      elementName = "method " + elementName;
    }
    else if (element instanceof PsiVariable) {
      elementName = PsiFormatUtil.formatVariable((PsiVariable)element,
                                                 PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_CONTAINING_CLASS,
                                                 PsiSubstitutor.EMPTY);
      if (element instanceof PsiField) {
        elementName = "field " + elementName;
      }
      else if (element instanceof PsiParameter) {
        elementName = "parameter " + elementName;
      }
      else {
        elementName = "variable " + elementName;
      }
    }
    else if (element instanceof PsiPackage) {
      elementName = ((PsiPackage)element).getQualifiedName();
      elementName = "package " + elementName;
    }
    if (element instanceof PsiKeyword &&
        (PsiKeyword.TRY.equals(element.getText()) || PsiKeyword.CATCH.equals(element.getText()) ||
         PsiKeyword.THROWS.equals(element.getText()))) {
      elementName = "exception thrown";
    }

    StringBuffer buffer = new StringBuffer();
    if (refCount > 0) {
      buffer.append(refCount);
      buffer.append(" usage");
      if (refCount > 1) {
        buffer.append("s");
      }
      if (elementName != null) {
        buffer.append(" of ");
        buffer.append(elementName);
      }
    }
    else {
      buffer.append("No usages");
      if (elementName != null) {
        buffer.append(" of ");
        buffer.append(elementName);
      }
// [jeka] Redundant "found"
//      buffer.append(" found");
    }
    buffer.append(" found (press Escape to remove the highlighting)");
    WindowManager.getInstance().getStatusBar(project).setInfo(buffer.toString());
  }
}