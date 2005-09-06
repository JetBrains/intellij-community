/*
 * Author: max
 * Date: Oct 9, 2001
 * Time: 8:43:17 PM
 */

package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.InspectCodePanel;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ui.configuration.LibrariesEditor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.content.*;
import com.intellij.util.containers.HashMap;
import org.jdom.Document;
import org.jdom.Element;

import javax.swing.*;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InspectionManagerEx extends InspectionManager implements JDOMExternalizable, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionManagerEx");
  private RefManager myRefManager;
  private ContentManager myContentManager;
  private AnalysisScope myCurrentScope;
  private final UIOptions myUIOptions;
  private final Project myProject;
  private List<JobDescriptor> myJobDescriptors;
  private InspectionResultsView myView = null;

  private HashMap<PsiElement, List<DerivedMethodsProcessor>> myDerivedMethodsRequests;
  private HashMap<PsiElement, List<DerivedClassesProcessor>> myDerivedClassesRequests;
  private HashMap<PsiElement, List<UsagesProcessor>> myMethodUsagesRequests;
  private HashMap<PsiElement, List<UsagesProcessor>> myFieldUsagesRequests;
  private HashMap<PsiElement, List<UsagesProcessor>> myClassUsagesRequests;
  private ProgressIndicator myProgressIndicator;
  private String myCurrentProfileName;


  public static final JobDescriptor BUILD_GRAPH = new JobDescriptor("Processing project usages in ");
  public static final JobDescriptor FIND_EXTERNAL_USAGES = new JobDescriptor("Processing external usages of ");
  private static final JobDescriptor LOCAL_ANALYSIS = new JobDescriptor("Analyzing code in ");


  public static final String SUPPRESS_INSPECTIONS_TAG_NAME = "noinspection";
  public static final String SUPPRESS_INSPECTIONS_ANNOTATION_NAME = "java.lang.SuppressWarnings";

  //for use in local comments
  private static final Pattern SUPPRESS_PATTERN = Pattern.compile("//\\s*" + SUPPRESS_INSPECTIONS_TAG_NAME + "\\s+(\\w+(,\\w+)*)");

  private InspectionProfile myExternalProfile = null;

  public InspectionManagerEx(Project project) {
    myProject = project;

    myUIOptions = new UIOptions();

    myRefManager = null;
    myCurrentScope = null;
    myContentManager = null;
    myCurrentProfileName = "Default";
  }

  public Project getProject() {
    return myProject;
  }

  public ContentManager getContentManager() {
    return myContentManager;
  }

  public void initComponent() { }

  public void disposeComponent() {
    cleanup();
  }

  public void projectOpened() {
    myContentManager = PeerFactory.getInstance().getContentFactory().createContentManager(new ComponentContentUI(), true, myProject);
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    ToolWindow toolWindow = toolWindowManager.registerToolWindow(ToolWindowId.INSPECTION,
                                                                 myContentManager.getComponent(),
                                                                 ToolWindowAnchor.BOTTOM);
    toolWindow.setIcon(IconLoader.getIcon("/general/toolWindowInspection.png"));
    new ContentManagerWatcher(toolWindow, myContentManager);
    myContentManager.addContentManagerListener(new ContentManagerAdapter() {
      public void contentRemoved(ContentManagerEvent event) {
        if (myView != null) {
          myView.dispose();
          myView = null;
        }
      }
    });
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(myUIOptions, element);

    Element profileElement = element.getChild("profile");
    if (profileElement != null) {
      myCurrentProfileName = profileElement.getAttributeValue("name");
    }
    else {
      myCurrentProfileName = "Default";
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(myUIOptions, element);

    Element profileElement = new Element("profile");
    profileElement.setAttribute("name", myCurrentProfileName);
    element.addContent(profileElement);
  }

  public InspectionProfile getCurrentProfile() {
    if (myExternalProfile != null) return myExternalProfile;
    final InspectionProfileManager inspectionProfileManager = InspectionProfileManager.getInstance();
    InspectionProfile profile = inspectionProfileManager.getProfile(myCurrentProfileName);
    if (profile == null) {
      final String[] avaliableProfileNames = inspectionProfileManager.getAvaliableProfileNames();
      if (avaliableProfileNames == null || avaliableProfileNames.length == 0){
        //can't be
        return null;
      }
      myCurrentProfileName = avaliableProfileNames[0];
      profile = inspectionProfileManager.getProfile(myCurrentProfileName);
    }
    return profile;
  }

  public ProblemDescriptor createProblemDescriptor(PsiElement psiElement,
                                                   String descriptionTemplate,
                                                   LocalQuickFix fix,
                                                   ProblemHighlightType highlightType) {
    return new ProblemDescriptorImpl(psiElement, descriptionTemplate, fix != null ? new LocalQuickFix[]{fix} : null, highlightType);
  }

  public ProblemDescriptor createProblemDescriptor(PsiElement psiElement,
                                                   String descriptionTemplate,
                                                   LocalQuickFix[] fixes,
                                                   ProblemHighlightType highlightType) {
    return new ProblemDescriptorImpl(psiElement, descriptionTemplate, fixes, highlightType);
  }

  public void projectClosed() {
    ToolWindowManager.getInstance(myProject).unregisterToolWindow(ToolWindowId.INSPECTION);
  }

  private void addView(InspectionResultsView view) {
    myView = view;
    ContentManager contentManager = getContentManager();

    Content content = PeerFactory.getInstance().getContentFactory().createContent(view, "FOOO", false);

    content.setDisplayName("Results for Inspection Profile \'" + view.getCurrentProfileName() + "\'");
    contentManager.addContent(content);
    contentManager.setSelectedContent(content);

    ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.INSPECTION).activate(null);
  }

  private static boolean isInspectionToolIdMentioned(String inspectionsList, String inspectionToolID) {
    String[] ids = inspectionsList.split("[,]");
    for (String id : ids) {
      if (id.equals(inspectionToolID) || id.equals("ALL")) return true;
    }
    return false;
  }

  public static boolean isToCheckMember(PsiDocCommentOwner owner, String inspectionToolID) {
    if (!isToCheckMemberInDocComment(owner, inspectionToolID)){
      return false;
    }
    if (!isToCheckMemberInAnnotation(owner, inspectionToolID)){
      return false;
    }
    PsiDocCommentOwner classContainer = PsiTreeUtil.getParentOfType(owner, PsiClass.class, true);
    while (classContainer != null) {
      if (!isToCheckMemberInDocComment(classContainer, inspectionToolID)){
        return false;
      }
      if (!isToCheckMemberInAnnotation(classContainer, inspectionToolID)){
        return false;
      }
      classContainer = PsiTreeUtil.getParentOfType(classContainer, PsiClass.class, true);
    }
    return true;
  }

  private static boolean isToCheckMemberInAnnotation(final PsiDocCommentOwner owner, final String inspectionToolID) {
    if (LanguageLevel.JDK_1_5.compareTo(owner.getManager().getEffectiveLanguageLevel()) > 0 ) return true;
    PsiModifierList modifierList = owner.getModifierList();
    if (modifierList != null) {
      PsiAnnotation annotation = modifierList.findAnnotation(SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
      if (annotation != null) {
        final PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
        if (attributes.length == 0) {
          return true;
        }
        final PsiAnnotationMemberValue attributeValue = attributes[0].getValue();
        if (attributeValue instanceof PsiArrayInitializerMemberValue) {
          final PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)attributeValue).getInitializers();
          for (PsiAnnotationMemberValue annotationMemberValue : initializers) {
            if (annotationMemberValue instanceof PsiLiteralExpression) {
              final Object value = ((PsiLiteralExpression)annotationMemberValue).getValue();
              if (value instanceof String) {
                if (isInspectionToolIdMentioned((String)value, inspectionToolID)) {
                  return false;
                }
              }
            }
          }
        } else if (attributeValue instanceof PsiLiteralExpression){
          final Object value = ((PsiLiteralExpression)attributeValue).getValue();
          if (value instanceof String) {
            if (isInspectionToolIdMentioned((String)value, inspectionToolID)) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  private static boolean isToCheckMemberInDocComment(final PsiDocCommentOwner owner, final String inspectionToolID) {
    PsiDocComment docComment = owner.getDocComment();
    if (docComment != null) {
      PsiDocTag inspectionTag = docComment.findTagByName(SUPPRESS_INSPECTIONS_TAG_NAME);
      if (inspectionTag != null && inspectionTag.getValueElement() != null) {
        String valueText = inspectionTag.getValueElement().getText();
        if (isInspectionToolIdMentioned(valueText, inspectionToolID)){
          return false;
        }
      }
    }
    return true;
  }

  public static boolean inspectionResultSuppressed(final PsiElement place, String id) {
    PsiStatement statement = PsiTreeUtil.getParentOfType(place, PsiStatement.class);
    if (statement != null) {
      PsiElement prev = PsiTreeUtil.skipSiblingsBackward(statement, new Class[]{PsiWhiteSpace.class});
      if (prev instanceof PsiComment) {
        String text = prev.getText();
        Matcher matcher = SUPPRESS_PATTERN.matcher(text);
        if (matcher.matches()) {
          return isInspectionToolIdMentioned(matcher.group(1), id);
        }
      }
    }

    PsiElement container = place;
    do {
      container = PsiTreeUtil.getParentOfType(container, PsiDocCommentOwner.class);
    }
    while (container instanceof PsiTypeParameter);
    PsiDocCommentOwner classContainer = PsiTreeUtil.getParentOfType(container, PsiDocCommentOwner.class, true);
    return container != null && !isToCheckMember((PsiDocCommentOwner)container, id) || (classContainer != null && !isToCheckMember(classContainer, id));
  }

  public UIOptions getUIOptions() {
    return myUIOptions;
  }

  public void setSplitterProportion(final float proportion) {
    myUIOptions.SPLITTER_PROPORTION = proportion;
  }

  public ToggleAction createToggleAutoscrollAction() {
    return myUIOptions.myAutoScrollToSourceHandler.createToggleAction();
  }

  public void installAutoscrollHandler(JTree tree) {
    myUIOptions.myAutoScrollToSourceHandler.install(tree);
  }

  public interface DerivedClassesProcessor extends Processor<PsiClass>{
  }

  public interface DerivedMethodsProcessor extends Processor<PsiMethod> {
  }

  public interface UsagesProcessor extends Processor<PsiReference> {
  }

  private interface Processor<T> {
    boolean process(T element);
  }

  public void enqueueClassUsagesProcessing(RefClass refClass, UsagesProcessor p) {
    if (myClassUsagesRequests == null) myClassUsagesRequests = new HashMap<PsiElement, List<UsagesProcessor>>();
    enqueueRequestImpl(refClass, myClassUsagesRequests, p);
  }

  public void enqueueDerivedClassesProcessing(RefClass refClass, DerivedClassesProcessor p) {
    if (myDerivedClassesRequests == null) myDerivedClassesRequests = new HashMap<PsiElement, List<DerivedClassesProcessor>>();
    enqueueRequestImpl(refClass, myDerivedClassesRequests, p);
  }

  public void enqueueDerivedMethodsProcessing(RefMethod refMethod, DerivedMethodsProcessor p) {
    if (refMethod.isConstructor() || refMethod.isStatic()) return;
    if (myDerivedMethodsRequests == null) myDerivedMethodsRequests = new HashMap<PsiElement, List<DerivedMethodsProcessor>>();
    enqueueRequestImpl(refMethod, myDerivedMethodsRequests, p);
  }

  public void enqueueFieldUsagesProcessor(RefField refField, UsagesProcessor p) {
    if (myFieldUsagesRequests == null) myFieldUsagesRequests = new HashMap<PsiElement, List<UsagesProcessor>>();
    enqueueRequestImpl(refField, myFieldUsagesRequests, p);
  }

  public void enqueueMethodUsagesProcessor(RefMethod refMethod, UsagesProcessor p) {
    if (myMethodUsagesRequests == null) myMethodUsagesRequests = new HashMap<PsiElement, List<UsagesProcessor>>();
    enqueueRequestImpl(refMethod, myMethodUsagesRequests, p);
  }

  private static <T extends Processor> void enqueueRequestImpl(RefElement refElement, Map<PsiElement,List<T>> requestMap, T processor) {
    List<T> requests = requestMap.get(refElement.getElement());
    if (requests == null) {
      requests = new ArrayList<T>();
      requestMap.put(refElement.getElement(), requests);
    }
    requests.add(processor);
  }

  private void cleanup() {
    myProgressIndicator = null;

    myDerivedMethodsRequests = null;
    myDerivedClassesRequests = null;
    myMethodUsagesRequests = null;
    myFieldUsagesRequests = null;
    myClassUsagesRequests = null;

    getCurrentProfile().cleanup();

    EntryPointsManager.getInstance(getProject()).cleanup();

    if (myRefManager != null) {
      myRefManager.cleanup();
      myRefManager = null;
      myCurrentScope = null;
    }
  }

  public void setCurrentScope(AnalysisScope currentScope) {
    myCurrentScope = currentScope;
  }

  public void doInspections(final AnalysisScope scope) {
    while (PsiManager.getInstance(getProject()).findClass("java.lang.Object") == null) {
      Messages.showMessageDialog(getProject(),
                                 "The JDK is not configured properly for this project. Inspection cannot proceed.",
                                 "Error",
                                 Messages.getErrorIcon());
      final ProjectJdk projectJdk = LibrariesEditor.chooseAndSetJDK(myProject);
      if (projectJdk == null) return;
    }

    if (myCurrentScope == null) {
      final InspectCodePanel itc = new InspectCodePanel(this, scope);

      itc.show();
      if (!itc.isOK()) return;


    }

    close();
    getContentManager().removeAllContents();

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        myCurrentScope = scope;
        launchInspections(scope);
      }
    });
  }



  public RefManager getRefManager() {
    if (myRefManager == null) {
      myRefManager = new RefManager(myProject, myCurrentScope);
    }

    return myRefManager;
  }

  public void launchInspectionsOffline(final AnalysisScope scope, OutputStream outStream) {
    cleanup();

    myCurrentScope = scope;
    final Element root = new Element("problems");
    final Document doc = new Document(root);


    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        performInspectionsWithProgress(scope);

        InspectionTool[] tools = getCurrentProfile().getInspectionTools();
        for (int i = 0; i < tools.length; i++) {
          InspectionTool tool = tools[i];
          if (getCurrentProfile().isToolEnabled(HighlightDisplayKey.find(tool.getShortName()))) {
            tool.exportResults(root);
          }
        }
      }
    });

    try {
      ((ProjectEx)getProject()).getMacroReplacements().substitute(doc.getRootElement(), SystemInfo.isFileSystemCaseSensitive);
      JDOMUtil.writeDocument(doc, outStream, "\n");
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public void processSearchRequests() {
    final PsiManager psiManager = PsiManager.getInstance(getProject());
    final PsiSearchHelper helper = psiManager.getSearchHelper();
    final RefManager refManager = getRefManager();
    final AnalysisScope scope = refManager.getScope();

    final SearchScope searchScope = new GlobalSearchScope() {
      public boolean contains(VirtualFile file) {
        return !scope.contains(file) || file.getFileType() != StdFileTypes.JAVA;
      }

      public int compare(VirtualFile file1, VirtualFile file2) {
        return 0;
      }

      public boolean isSearchInModuleContent(Module aModule) {
        return true;
      }

      public boolean isSearchInLibraries() {
        return false;
      }
    };

    final ProgressIndicator progress = myProgressIndicator == null ? null : new ProgressWrapper(myProgressIndicator);
    ProgressManager.getInstance().runProcess(new Runnable() {
      public void run() {
        if (myDerivedClassesRequests != null) {
          List<PsiElement> sortedIDs = getSortedIDs(myDerivedClassesRequests);
          for (int i = 0; i < sortedIDs.size(); i++) {
            PsiClass psiClass = (PsiClass)sortedIDs.get(i);
            incrementJobDoneAmount(FIND_EXTERNAL_USAGES, psiClass.getQualifiedName());

            final List<DerivedClassesProcessor> processors = myDerivedClassesRequests.get(psiClass);
            helper.processInheritors(new PsiElementProcessor<PsiClass>() {
              public boolean execute(PsiClass inheritor) {
                if (scope.contains(inheritor)) return true;
                DerivedClassesProcessor[] processorsArrayed = processors.toArray(new DerivedClassesProcessor[processors.size()]);
                for (int j = 0; j < processorsArrayed.length; j++) {
                  DerivedClassesProcessor processor = processorsArrayed[j];
                  if (!processor.process(inheritor)) {
                    processors.remove(processor);
                  }
                }
                return processors.size() > 0;
              }
            }, psiClass, searchScope, false);
          }

          myDerivedClassesRequests = null;
        }

        if (myDerivedMethodsRequests != null) {
          List<PsiElement> sortedIDs = getSortedIDs(myDerivedMethodsRequests);
          for (int i = 0; i < sortedIDs.size(); i++) {
            PsiMethod psiMethod = (PsiMethod)sortedIDs.get(i);
            final RefMethod refMethod = (RefMethod)refManager.getReference(psiMethod);

            incrementJobDoneAmount(FIND_EXTERNAL_USAGES, RefUtil.getQualifiedName(refMethod));

            final List<DerivedMethodsProcessor> processors = myDerivedMethodsRequests.get(psiMethod);
            helper.processOverridingMethods(new PsiElementProcessor<PsiMethod>() {
              public boolean execute(PsiMethod derivedMethod) {
                if (scope.contains(derivedMethod)) return true;
                DerivedMethodsProcessor[] processorsArrayed = processors.toArray(new DerivedMethodsProcessor[processors.size()]);
                for (int j = 0; j < processorsArrayed.length; j++) {
                  DerivedMethodsProcessor processor = processorsArrayed[j];
                  if (!processor.process(derivedMethod)) {
                    processors.remove(processor);
                  }
                }

                return processors.size() > 0;
              }
            }, psiMethod, searchScope, true);
          }

          myDerivedMethodsRequests = null;
        }

        if (myFieldUsagesRequests != null) {
          List<PsiElement> sortedIDs = getSortedIDs(myFieldUsagesRequests);
          for (int i = 0; i < sortedIDs.size(); i++) {
            PsiField psiField = (PsiField)sortedIDs.get(i);
            final List<UsagesProcessor> processors = myFieldUsagesRequests.get(psiField);

            incrementJobDoneAmount(FIND_EXTERNAL_USAGES, RefUtil.getQualifiedName(refManager.getReference(psiField)));

            helper.processReferences(createReferenceProcessor(processors), psiField, searchScope, false);
          }

          myFieldUsagesRequests = null;
        }

        if (myClassUsagesRequests != null) {
          List<PsiElement> sortedIDs = getSortedIDs(myClassUsagesRequests);
          for (int i = 0; i < sortedIDs.size(); i++) {
            PsiClass psiClass = (PsiClass)sortedIDs.get(i);
            final List<UsagesProcessor> processors = myClassUsagesRequests.get(psiClass);

            incrementJobDoneAmount(FIND_EXTERNAL_USAGES, psiClass.getQualifiedName());

            helper.processReferences(createReferenceProcessor(processors), psiClass, searchScope, false);
          }

          myClassUsagesRequests = null;
        }

        if (myMethodUsagesRequests != null) {
          List<PsiElement> sortedIDs = getSortedIDs(myMethodUsagesRequests);
          for (int i = 0; i < sortedIDs.size(); i++) {
            PsiMethod psiMethod = (PsiMethod)sortedIDs.get(i);
            final List<UsagesProcessor> processors = myMethodUsagesRequests.get(psiMethod);

            incrementJobDoneAmount(FIND_EXTERNAL_USAGES, RefUtil.getQualifiedName(refManager.getReference(psiMethod)));

            helper.processReferencesIncludingOverriding(createReferenceProcessor(processors), psiMethod, searchScope);
          }

          myMethodUsagesRequests = null;
        }
      }
    }, progress);
  }

  private static class ProgressWrapper extends ProgressIndicatorBase {
    private ProgressIndicator myOriginal;

    public ProgressWrapper(final ProgressIndicator original) {
      myOriginal = original;
    }

    public boolean isCanceled() {
      return myOriginal.isCanceled();
    }
  }

  private int getRequestCount() {
    int sum = 0;

    sum += getRequestListSize(myClassUsagesRequests);
    sum += getRequestListSize(myDerivedClassesRequests);
    sum += getRequestListSize(myDerivedMethodsRequests);
    sum += getRequestListSize(myFieldUsagesRequests);
    sum += getRequestListSize(myMethodUsagesRequests);

    return sum;
  }

  private static int getRequestListSize(HashMap list) {
    if (list == null) return 0;
    return list.size();
  }

  private static List<PsiElement> getSortedIDs(Map<PsiElement,?> requests) {
    List<PsiElement> result = new ArrayList<PsiElement>();
    for (PsiElement id : requests.keySet()) {
      result.add(id);
    }

    Collections.sort(result, new Comparator<PsiElement>() {
      public int compare(PsiElement o1, PsiElement o2) {
        return o1.getContainingFile().getName().compareTo(o2.getContainingFile().getName());
      }
    });

    return result;
  }

  private PsiReferenceProcessor createReferenceProcessor(final List<UsagesProcessor> processors) {
    return new PsiReferenceProcessor() {
      public boolean execute(PsiReference reference) {
        AnalysisScope scope = getRefManager().getScope();
        if ((scope.contains(reference.getElement()) && reference.getElement().getContainingFile() instanceof PsiJavaFile) ||
            PsiTreeUtil.getParentOfType(reference.getElement(), PsiDocComment.class) != null) {
          return true;
        }
        UsagesProcessor[] processorsArrayed = processors.toArray(new UsagesProcessor[processors.size()]);
        for (int j = 0; j < processorsArrayed.length; j++) {
          UsagesProcessor processor = processorsArrayed[j];
          if (!processor.process(reference)) {
            processors.remove(processor);
          }
        }

        return processors.size() > 0;
      }
    };
  }

  private void launchInspections(final AnalysisScope scope) {
    cleanup();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      }
    });


    LOG.info("Code inspection started");

    Runnable runInspection = new Runnable() {
      public void run() {
        performInspectionsWithProgress(scope);
      }
    };

    if (!ApplicationManager.getApplication().runProcessWithProgressSynchronously(runInspection, "Inspecting Code...", true, myProject)) return;

    InspectionResultsView view = new InspectionResultsView(myProject, getCurrentProfile(), scope);
    if (!view.update()) {
      Messages.showMessageDialog(myProject,
                                 "No suspicious code found",
                                 "Code Inspection",
                                 Messages.getInformationIcon());
    }
    else {
      addView(view);
    }
  }

  private void performInspectionsWithProgress(final AnalysisScope scope) {
    try {
      myProgressIndicator = ProgressManager.getInstance().getProgressIndicator();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          try {
            PsiManager.getInstance(myProject).startBatchFilesProcessingMode();
            getRefManager().inspectionReadActionStarted();
            EntryPointsManager.getInstance(getProject()).resolveEntryPoints(getRefManager());

            InspectionTool[] tools = getCurrentProfile().getInspectionTools();
            ArrayList<LocalInspectionToolWrapper> localTools = initJobDescriptors(tools, scope);

            List<InspectionTool> needRepeatSearchRequest = new ArrayList<InspectionTool>();
            runTools(tools, localTools, needRepeatSearchRequest, scope);
            performPostRunFindUsages(needRepeatSearchRequest);
          }
          catch (ProcessCanceledException e) {
            cleanup();
            throw e;
          }
          finally {
            PsiManager.getInstance(myProject).finishBatchFilesProcessingMode();
          }
        }
      });
    }
    finally {
      if (myRefManager != null) {
        getRefManager().inspectionReadActionFinished();
      }
    }
  }

  private void performPostRunFindUsages(List<InspectionTool> needRepeatSearchRequest) {
    FIND_EXTERNAL_USAGES.setTotalAmount(getRequestCount() * 2);

    do {
      processSearchRequests();
      InspectionTool[] requestors = needRepeatSearchRequest.toArray(new InspectionTool[needRepeatSearchRequest.size()]);
      for (InspectionTool requestor : requestors) {
        if (!requestor.queryExternalUsagesRequests()) needRepeatSearchRequest.remove(requestor);
      }
      int oldSearchRequestCount = FIND_EXTERNAL_USAGES.getTotalAmount();
      float proportion = FIND_EXTERNAL_USAGES.getProgress();
      int totalAmount = oldSearchRequestCount + getRequestCount() * 2;
      FIND_EXTERNAL_USAGES.setTotalAmount(totalAmount);
      FIND_EXTERNAL_USAGES.setDoneAmount((int)(totalAmount * proportion));
    }
    while (needRepeatSearchRequest.size() > 0);
  }

  private void runTools(InspectionTool[] tools,
                        final ArrayList<LocalInspectionToolWrapper> localTools,
                        List<InspectionTool> needRepeatSearchRequest,
                        final AnalysisScope scope) {
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    for (InspectionTool tool : tools) {
      if (getCurrentProfile().isToolEnabled(HighlightDisplayKey.find(tool.getShortName()))) tool.initialize(this);
    }

    try {
      scope.accept(new PsiRecursiveElementVisitor() {
        public void visitReferenceExpression(PsiReferenceExpression expression) {
        }

        @Override
        public void visitFile(PsiFile file) {
          incrementJobDoneAmount(LOCAL_ANALYSIS, file.getVirtualFile().getPresentableUrl());
          for (int i = 0; i < localTools.size(); i++) {
            LocalInspectionToolWrapper tool = localTools.get(i);
            tool.processFile(file);
            psiManager.dropResolveCaches();
          }
        }
      });
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
    }

    for (InspectionTool tool : tools) {
      if (getCurrentProfile().isToolEnabled(HighlightDisplayKey.find(tool.getShortName())) &&
          !(tool instanceof LocalInspectionToolWrapper)) {
        try {
          tool.runInspection(scope);

          if (tool.queryExternalUsagesRequests()) {
            needRepeatSearchRequest.add(tool);
          }
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
  }

  private ArrayList<LocalInspectionToolWrapper> initJobDescriptors(InspectionTool[] tools,
                                                                   final AnalysisScope scope) {
    ArrayList<LocalInspectionToolWrapper> localTools = new ArrayList<LocalInspectionToolWrapper>();
    myJobDescriptors = new ArrayList<JobDescriptor>();
    for (InspectionTool tool : tools) {
      if (getCurrentProfile().isToolEnabled(HighlightDisplayKey.find(tool.getShortName()))) {
        if (tool instanceof LocalInspectionToolWrapper) {
          LocalInspectionToolWrapper wrapper = (LocalInspectionToolWrapper) tool;
          localTools.add(wrapper);
          appendJobDescriptor(LOCAL_ANALYSIS);
        } else {
          JobDescriptor[] jobDescriptors = tool.getJobDescriptors();
          for (JobDescriptor jobDescriptor : jobDescriptors) {
            appendJobDescriptor(jobDescriptor);
          }
        }
      }
    }

    BUILD_GRAPH.setTotalAmount(scope.getFileCount());
    LOCAL_ANALYSIS.setTotalAmount(scope.getFileCount());
    return localTools;
  }

  private void appendJobDescriptor(JobDescriptor job) {
    if (!myJobDescriptors.contains(job)) {
      myJobDescriptors.add(job);
      job.setDoneAmount(0);
    }
  }

  public void close() {
    getContentManager().removeAllContents();
    cleanup();
  }

  public class UIOptions implements JDOMExternalizable {
      public boolean AUTOSCROLL_TO_SOURCE = false;
      public float SPLITTER_PROPORTION = 0.5f;
      public boolean GROUP_BY_SEVERITY = false;
      public boolean ANALYZE_TEST_SOURCES = true;
      public int SCOPE_TYPE = 1;
      public final AutoScrollToSourceHandler myAutoScrollToSourceHandler;
      public final GroupBySeverityAction myGroupBySeverityAction;
      public UIOptions() {
        myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
          protected boolean isAutoScrollMode() {
            return AUTOSCROLL_TO_SOURCE;
          }

          protected void setAutoScrollMode(boolean state) {
            AUTOSCROLL_TO_SOURCE = state;
          }
        };
        myGroupBySeverityAction = new GroupBySeverityAction();
      }

      public void readExternal(Element element) throws InvalidDataException {
        DefaultJDOMExternalizer.readExternal(this, element);
      }

      public void writeExternal(Element element) throws WriteExternalException {
        DefaultJDOMExternalizer.writeExternal(this, element);
      }
  }

  private class GroupBySeverityAction extends ToggleAction {
    public GroupBySeverityAction() {
      super("Group by Severity", "Group Inspections By Severity", IconLoader.getIcon("/nodes/sortBySeverity.png"));
    }

    public boolean isSelected(AnActionEvent e) {
      return myUIOptions.GROUP_BY_SEVERITY;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      myUIOptions.GROUP_BY_SEVERITY = state;
      myView.update();
    }
  }

  public AnAction createGroupBySeverityAction(){
    return myUIOptions.myGroupBySeverityAction;
  }

  public String getComponentName() {
    return "InspectionManager";
  }

  public void refreshViews() {
    myView.update();
  }

  public void incrementJobDoneAmount(JobDescriptor job, String message) {
    if (myProgressIndicator == null) return;

    ProgressManager.getInstance().checkCanceled();

    int old = job.getDoneAmount();
    job.setDoneAmount(old + 1);

    int jobCount = myJobDescriptors.size();
    float totalProgress = 0;
    for (int i = 0; i < myJobDescriptors.size(); i++) {
      totalProgress += myJobDescriptors.get(i).getProgress();
    }

    totalProgress /= jobCount;

    myProgressIndicator.setFraction(totalProgress);
    myProgressIndicator.setText(job.getDisplayName() + " " + message);
  }

  public void setProfile(InspectionProfile profile) {
    myCurrentProfileName = profile.getName();
  }

  public void setExternalProfile(InspectionProfile profile) {
    myExternalProfile = profile;
  }

  public boolean areResultsShown() {
    return myView != null;
  }
}
