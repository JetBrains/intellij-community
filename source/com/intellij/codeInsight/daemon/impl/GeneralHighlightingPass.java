package com.intellij.codeInsight.daemon.impl;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.CodeInsightColors;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.j2ee.J2EERolesUtil;
import com.intellij.j2ee.ejb.EjbUtil;
import com.intellij.j2ee.ejb.role.EjbImplMethodRole;
import com.intellij.j2ee.ejb.role.EjbMethodRole;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.TextRange;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.DependencyRule;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.psi.util.PsiSuperMethodUtil;
import gnu.trove.THashSet;

import javax.swing.*;
import java.util.*;

public class GeneralHighlightingPass extends TextEditorHighlightingPass {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass");
  private static final Icon OVERRIDING_METHOD_ICON = IconLoader.getIcon("/gutter/overridingMethod.png");
  private static final Icon IMPLEMENTING_METHOD_ICON = IconLoader.getIcon("/gutter/implementingMethod.png");

  private final Project myProject;
  private final PsiFile myFile;
  private final Document myDocument;
  private final int myStartOffset;
  private final int myEndOffset;
  private final boolean myUpdateAll;
  private final boolean myCompiled;

  private final HighlightVisitor[] myHighlightVisitors;

  private HighlightInfo[] myHighlights = HighlightInfo.EMPTY_ARRAY;
  private LineMarkerInfo[] myMarkers = LineMarkerInfo.EMPTY_ARRAY;

  private final DaemonCodeAnalyzerSettings mySettings = DaemonCodeAnalyzerSettings.getInstance();

  public GeneralHighlightingPass(Project project,
                                 PsiFile file,
                                 Document document,
                                 int startOffset,
                                 int endOffset,
                                 boolean isCompiled,
                                 boolean updateAll) {
    super(document);
    myProject = project;
    myFile = file;
    myDocument = document;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myUpdateAll = updateAll;
    myCompiled = isCompiled;

    myHighlightVisitors = createHighlightVisitors();
    LOG.assertTrue(myFile.isValid());
  }

  private HighlightVisitor[] createHighlightVisitors() {
    HighlightVisitor[] highlightVisitors = myProject.getComponents(HighlightVisitor.class);
    for (int i = 0; i < highlightVisitors.length; i++) {
      final HighlightVisitor highlightVisitor = highlightVisitors[i];
      highlightVisitor.init();
    }
    return highlightVisitors;
  }

  /**
   * @fabrique *
   */
  public Project getProject() {
    return myProject;
  }

  public void doCollectInformation(ProgressIndicator progress) {
    /*
    if (myUpdateAll){
      AlternativeWay.processFile(myFile);
    }
    */
    final PsiElement[] psiRoots = myFile.getPsiRoots();

    if (myUpdateAll) {
      DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
      final RefCountHolder refCountHolder = daemonCodeAnalyzer.getFileStatusMap().getRefCountHolder(myDocument, myFile);
      setRefCountHolders(refCountHolder);

      PsiElement dirtyScope = daemonCodeAnalyzer.getFileStatusMap().getFileDirtyScope(myDocument, FileStatusMap.NORMAL_HIGHLIGHTERS);
      if (dirtyScope != null) {
        if (dirtyScope instanceof PsiFile) {
          refCountHolder.clear();
        }
        else {
          refCountHolder.removeInvalidRefs();
        }
      }
    }
    else {
      setRefCountHolders(null);
    }
    List<HighlightInfo> result = new ArrayList<HighlightInfo>();
    try {
      for (int j = 0; j < psiRoots.length; j++) {
        final PsiElement psiRoot = psiRoots[j];
        long time = System.currentTimeMillis();
        PsiElement[] elements = CodeInsightUtil.getElementsInRange(psiRoot, myStartOffset, myEndOffset);
        LOG.debug("Elements collected for: " + (System.currentTimeMillis() - time) / 1000.0 + "s");
        time = System.currentTimeMillis();

        myMarkers = collectLineMarkers(elements);
        LOG.debug("Line markers collected for: " + (System.currentTimeMillis() - time) / 1000.0 + "s");

        Collection<HighlightInfo> highlights1 = collectHighlights(elements);
        Collection<HighlightInfo> highlights2 = collectTextHighlights();
        addHighlights(result, highlights1);
        addHighlights(result, highlights2);
      }
    }
    finally {
      if (myHighlightVisitors != null) {
        setRefCountHolders(null);
      }
    }
    myHighlights = result.toArray(new HighlightInfo[result.size()]);
  }

  private void addHighlights(List<HighlightInfo> result, Collection<HighlightInfo> highlights) {
    if (myCompiled) {
      for (Iterator<HighlightInfo> iterator = highlights.iterator(); iterator.hasNext();) {
        final HighlightInfo info = iterator.next();
        if (info.getSeverity() == HighlightInfo.INFORMATION) {
          result.add(info);
        }
      }
    }
    else {
      result.addAll(highlights);
    }
  }

  private void setRefCountHolders(final RefCountHolder refCountHolder) {
    for (int i = 0; i < myHighlightVisitors.length; i++) {
      HighlightVisitor visitor = myHighlightVisitors[i];
      visitor.setRefCountHolder(refCountHolder);
    }
  }

  public void doApplyInformationToEditor() {
    UpdateHighlightersUtil.setLineMarkersToEditor(myProject, myDocument, myStartOffset, myEndOffset,
                                                  myMarkers, UpdateHighlightersUtil.NORMAL_MARKERS_GROUP);

    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, myStartOffset, myEndOffset,
                                                   myHighlights, UpdateHighlightersUtil.NORMAL_HIGHLIGHTERS_GROUP);
  }

  public int getPassId() {
    return myUpdateAll ? Pass.UPDATE_ALL : Pass.UPDATE_VISIBLE;
  }

  public LineMarkerInfo[] queryLineMarkers() {
    try {
      PsiElement[] elements = CodeInsightUtil.getElementsInRange(myFile, myStartOffset, myEndOffset);
      return collectLineMarkers(elements);
    }
    catch (ProcessCanceledException e) {
      return null;
    }
  }

  //for tests only
  public HighlightInfo[] getHighlights() {
    return myHighlights;
  }

  private Collection<HighlightInfo> collectHighlights(PsiElement[] elements) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    Set<PsiElement> skipParentsSet = new THashSet<PsiElement>();
    Set<HighlightInfo> gotHighlights = new THashSet<HighlightInfo>();
    long totalTime = 0;
    if (LOG.isDebugEnabled()) {
      totalTime = System.currentTimeMillis();
    }

    List<HighlightVisitor> visitors = new ArrayList<HighlightVisitor>();
    for (int i = 0; i < myHighlightVisitors.length; i++) {
      HighlightVisitor visitor = myHighlightVisitors[i];
      if (visitor.suitableForFile(myFile)) visitors.add(visitor);
    }

    HighlightInfoFilter[] filters = ApplicationManager.getApplication().getComponents(HighlightInfoFilter.class);

    for (int i = 0; i < elements.length; i++) {
      ProgressManager.getInstance().checkCanceled();

      PsiElement element = elements[i];
      if (skipParentsSet.contains(element)) {
        skipParentsSet.add(element.getParent());
        continue;
      }

      HighlightInfoHolder holder = new HighlightInfoHolder(myFile, filters);
      for (int v = 0; v < visitors.size(); v++) {
        HighlightVisitor visitor = visitors.get(v);
        visitor.visit(element, holder);
      }

      HighlightInfo[] highlights = holder.toArray(new HighlightInfo[holder.size()]);

      for (int j = 0; j < highlights.length; j++) {
        HighlightInfo info = highlights[j];
        // have to filter out already obtained highlights
        if (gotHighlights.contains(info)) continue;

        gotHighlights.add(info);
        if (info.getSeverity() == HighlightInfo.ERROR) {
          skipParentsSet.add(element.getParent());
        }
      }
    }

    if (LOG.isDebugEnabled()) {
      //if(maxVisitElement != null){
      //  LOG.debug("maxVisitTime = " + maxVisitTime);
      //  LOG.debug("maxVisitElement = " + maxVisitElement+ " ");
      //}
      LOG.debug("totalTime = " + (System.currentTimeMillis() - totalTime) / (double)1000 + "s for " + elements.length + " elements");
    }

    return gotHighlights;
  }

  private Collection<HighlightInfo> collectTextHighlights() {
    PsiManager psiManager = myFile.getManager();
    PsiSearchHelper helper = psiManager.getSearchHelper();
    TodoItem[] todoItems = helper.findTodoItems(myFile, myStartOffset, myEndOffset);
    List<HighlightInfo> list = new ArrayList<HighlightInfo>();
    for (int i = 0; i < todoItems.length; i++) {
      TodoItem todoItem = todoItems[i];
      TextRange range = todoItem.getTextRange();
      String description = myDocument.getCharsSequence().subSequence(range.getStartOffset(), range.getEndOffset()).toString();
      HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.TODO, range, description,
                                                             todoItem.getPattern().getAttributes().getTextAttributes());
      list.add(info);
    }
    collectDependencyProblems(list);
    return list;
  }

  private void collectDependencyProblems(final List<HighlightInfo> list) {
    if (myUpdateAll && myFile instanceof PsiJavaFile && myFile.isPhysical() && myFile.getVirtualFile() != null &&
        mySettings.getInspectionProfile().isToolEnabled(HighlightDisplayKey.ILLEGAL_DEPENDENCY)) {
      DependenciesBuilder builder = new DependenciesBuilder(myProject, new AnalysisScope(myFile, AnalysisScope.SOURCE_JAVA_FILES));
      final DependencyValidationManager validationManager = DependencyValidationManager.getInstance(myProject);
      builder.analyzeFileDependencies(myFile, new DependenciesBuilder.DependencyProcessor() {
        public void process(PsiElement place, PsiElement dependency) {
          PsiFile dependencyFile = dependency.getContainingFile();
          if (dependencyFile != null && dependencyFile.isPhysical() && dependencyFile.getVirtualFile() != null) {
            final DependencyRule rule = validationManager.getViolatorDependencyRule(myFile, dependencyFile);
            if (rule != null) {
              HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ILLEGAL_DEPENDENCY, place,
                                                                     "Illegal dependency. Violated rule: \"" + rule.getDisplayText() +
                                                                     "\"");
              if (info != null) {
                list.add(info);
              }
            }
          }
        }
      });
    }
  }

  private LineMarkerInfo[] collectLineMarkers(PsiElement[] elements) throws ProcessCanceledException {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    List<LineMarkerInfo> array = new ArrayList<LineMarkerInfo>();
    for (int i = 0; i < elements.length; i++) {
      ProgressManager.getInstance().checkCanceled();

      PsiElement element = elements[i];
      LineMarkerInfo info = getLineMarkerInfo(element);
      if (info != null) {
        array.add(info);
      }
    }
    return array.toArray(new LineMarkerInfo[array.size()]);
  }

  private LineMarkerInfo getLineMarkerInfo(PsiElement element) {
    if (element instanceof PsiIdentifier && element.getParent() instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element.getParent();
      int offset = element.getTextRange().getStartOffset();
      EjbMethodRole role = J2EERolesUtil.getEjbRole(method);

      if (role instanceof EjbImplMethodRole && EjbUtil.findEjbDeclarations(method).length != 0) {
        LineMarkerInfo info = new LineMarkerInfo(LineMarkerInfo.OVERRIDING_METHOD, method, offset, IMPLEMENTING_METHOD_ICON);
        return info;
      }

      PsiMethod[] methods = PsiSuperMethodUtil.findSuperMethods(method, false);
      if (methods.length > 0) {
        boolean overrides = false;
        if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
          overrides = true;
        }
        else if (!methods[0].hasModifierProperty(PsiModifier.ABSTRACT)) {
          overrides = true;
        }

        LineMarkerInfo info = new LineMarkerInfo(LineMarkerInfo.OVERRIDING_METHOD, method, offset,
                                                 overrides ? OVERRIDING_METHOD_ICON : IMPLEMENTING_METHOD_ICON);
        return info;
      }
    }

    if (mySettings.SHOW_METHOD_SEPARATORS && element.getFirstChild() == null) {
      PsiElement element1 = element;
      boolean isMember = false;
      while (element1 != null && !(element1 instanceof PsiFile) && element1.getPrevSibling() == null) {
        element1 = element1.getParent();
        if (element1 instanceof PsiMember) {
          isMember = true;
          break;
        }
      }
      if (isMember && !(element1 instanceof PsiAnonymousClass || element1.getParent() instanceof PsiAnonymousClass)) {
        boolean drawSeparator = false;
        int category = getCategory(element1);
        for (PsiElement child = element1.getPrevSibling(); child != null; child = child.getPrevSibling()) {
          int category1 = getCategory(child);
          if (category1 == 0) continue;
          drawSeparator = category != 1 || category1 != 1;
          break;
        }

        if (drawSeparator) {
          LineMarkerInfo info = new LineMarkerInfo(LineMarkerInfo.METHOD_SEPARATOR, element, element.getTextRange().getStartOffset(), null);
          EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
          info.separatorColor = scheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR);
          info.separatorPlacement = SeparatorPlacement.TOP;
          return info;
        }
      }
    }

    return null;
  }

  private static int getCategory(PsiElement element) {
    if (element instanceof PsiField) return 1;
    if (element instanceof PsiClass || element instanceof PsiClassInitializer) return 2;
    if (element instanceof PsiMethod) {
      if (((PsiMethod)element).hasModifierProperty(PsiModifier.ABSTRACT)) {
        return 1;
      }
      String text = element.getText();
      if (text.indexOf('\n') < 0 && text.indexOf('\r') < 0) {
        return 1;
      }
      else {
        return 2;
      }
    }
    return 0;
  }
}