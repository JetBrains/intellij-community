/*
 * Author: max
 * Date: Oct 9, 2001
 * Time: 8:43:17 PM
 */

package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.export.ExportToHTMLDialog;
import com.intellij.codeInspection.export.HTMLExportFrameMaker;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.InspectCodePanel;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.actions.NextOccurenceToolbarAction;
import com.intellij.ide.actions.PreviousOccurenceToolbarAction;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
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
import java.io.File;
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
  public static final JobDescriptor LOCAL_ANALYSIS = new JobDescriptor("Analyzing code in ");

  public static final String HELP_ID = "codeInspection";

  public static final String SUPPRESS_INSPECTIONS_TAG_NAME = "noinspection";
  public static final String SUPPRESS_INSPECTIONS_ANNOTATION_NAME = "com.intellij.util.annotations.NoInspection";

  //for use in local comments
  public static final Pattern SUPPRESS_PATTERN = Pattern.compile("//" + SUPPRESS_INSPECTIONS_TAG_NAME + " (\\w+(,\\w+)*)");

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
        myView = null;
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
    InspectionProfile profile = InspectionProfileManager.getInstance().getProfile(myCurrentProfileName);
    if (profile == null) {
      myCurrentProfileName = "Default";
      profile = InspectionProfileManager.getInstance().getProfile(myCurrentProfileName);
    }
    return profile;
  }

  public ProblemDescriptor createProblemDescriptor(PsiElement psiElement,
                                                   String descriptionTemplate,
                                                   LocalQuickFix fix,
                                                   ProblemHighlightType highlightType) {
    return new ProblemDescriptorImpl(psiElement, descriptionTemplate, fix, highlightType);
  }

  public void projectClosed() {
    ToolWindowManager.getInstance(myProject).unregisterToolWindow(ToolWindowId.INSPECTION);
  }

  private void addView(InspectionResultsView view) {
    myView = view;
    ContentManager contentManager = getContentManager();

    Content content = PeerFactory.getInstance().getContentFactory().createContent(view, "FOOO", false);

    content.setDisplayName("Inspection Results");
    contentManager.addContent(content);
    contentManager.setSelectedContent(content);

    ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.INSPECTION).activate(null);
  }

  private boolean isInspectionToolIdMentioned(String inspectionsList, String inspectionToolID) {
    String[] ids = inspectionsList.split(",");
    for (int i = 0; i < ids.length; i++) {
      String id = ids[i];
      if (id.equals(inspectionToolID) || id.equals("ALL")) return true;
    }
    return false;
  }

  public boolean isToCheckMember(PsiDocCommentOwner member, String inspectionToolID) {
    PsiDocComment docComment = member.getDocComment();
    if (docComment != null) {
      PsiDocTag inspectionTag = docComment.findTagByName(SUPPRESS_INSPECTIONS_TAG_NAME);
      if (inspectionTag != null) {

        String valueText = inspectionTag.getValueElement().getText();
        return !isInspectionToolIdMentioned(valueText, inspectionToolID);
      }
    }

    PsiModifierList modifierList = member.getModifierList();
    if (modifierList != null) {
      PsiAnnotation annotation = modifierList.findAnnotation(SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
      if (annotation != null) {
        PsiAnnotationMemberValue attributeValue = annotation.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
        if (attributeValue instanceof PsiArrayInitializerMemberValue) {
          PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)attributeValue).getInitializers();
          for (int i = 0; i < initializers.length; i++) {
            PsiAnnotationMemberValue initializer = initializers[i];
            if (initializer instanceof PsiLiteralExpression) {
              Object value = ((PsiLiteralExpression)initializer).getValue();
              if (inspectionToolID.equals(value) || "ALL".equals(value)) return false;
            }
          }
        }
      }
    }
    return true;
  }

  public boolean inspectionResultSuppressed(PsiElement place, LocalInspectionTool tool) {
    PsiStatement statement = PsiTreeUtil.getParentOfType(place, PsiStatement.class);
    if (statement != null) {
      PsiElement prev = PsiTreeUtil.skipSiblingsBackward(statement, new Class[]{PsiWhiteSpace.class});
      if (prev instanceof PsiComment) {
        String text = prev.getText();
        Matcher matcher = SUPPRESS_PATTERN.matcher(text);
        if (matcher.matches()) {
          return isInspectionToolIdMentioned(matcher.group(1), tool.getID());
        }
      }
    }
    return false;
  }

  public interface DerivedClassesProcessor {
    boolean process(PsiClass inheritor);
  }

  public interface DerivedMethodsProcessor {
    boolean process(PsiMethod derivedMethod);
  }

  public interface UsagesProcessor {
    boolean process(PsiReference psiReference);
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

  private static void enqueueRequestImpl(RefElement refElement, HashMap requestMap, Object processor) {
    ArrayList requests = (ArrayList)requestMap.get(refElement.getElement());
    if (requests == null) {
      requests = new ArrayList();
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
    }
  }

  public void setCurrentScope(AnalysisScope currentScope) {
    myCurrentScope = currentScope;
  }

  public void doInspections(final AnalysisScope scope, boolean showDialog) {
    while (PsiManager.getInstance(getProject()).findClass("java.lang.Object") == null) {
      Messages.showMessageDialog(getProject(),
                                 "The JDK is not configured properly for this project. Inspection cannot proceed.",
                                 "Error",
                                 Messages.getErrorIcon());
      final ProjectJdk projectJdk = LibrariesEditor.chooseAndSetJDK(myProject);
      if (projectJdk == null) return;
    }

    if (showDialog || myCurrentScope == null) {
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

  private void rerun() {
    if (myCurrentScope.isValid()) {
      cleanup();
      doInspections(myCurrentScope, false);
    }
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

        InspectionTool[] tools = getCurrentProfile().getInspectionTools(myProject);
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
    final PsiSearchHelper helper = PsiManager.getInstance(getProject()).getSearchHelper();
    final RefManager refManager = getRefManager();
    final AnalysisScope scope = refManager.getScope();

    final SearchScope searchScope = new GlobalSearchScope() {
      public boolean contains(VirtualFile file) {
        return !scope.contains(file);
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

    ProgressManager.getInstance().runProcess(new Runnable() {
      public void run() {
        if (myDerivedClassesRequests != null) {
          ArrayList sortedIDs = getSortedIDs(myDerivedClassesRequests);
          for (int i = 0; i < sortedIDs.size(); i++) {
            PsiClass psiClass = (PsiClass)sortedIDs.get(i);
            incrementJobDoneAmount(FIND_EXTERNAL_USAGES, psiClass.getQualifiedName());

            final List<DerivedClassesProcessor> processors = myDerivedClassesRequests.get(psiClass);
            helper.processInheritors(new PsiBaseElementProcessor<PsiClass>() {
              public boolean execute(PsiClass element) {
                PsiClass inheritor = element;
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
          ArrayList sortedIDs = getSortedIDs(myDerivedMethodsRequests);
          for (int i = 0; i < sortedIDs.size(); i++) {
            PsiMethod psiMethod = (PsiMethod)sortedIDs.get(i);
            final RefMethod refMethod = (RefMethod)refManager.getReference(psiMethod);

            incrementJobDoneAmount(FIND_EXTERNAL_USAGES, RefUtil.getQualifiedName(refMethod));

            final List<DerivedMethodsProcessor> processors = myDerivedMethodsRequests.get(psiMethod);
            helper.processOverridingMethods(new PsiBaseElementProcessor<PsiMethod>() {
              public boolean execute(PsiMethod element) {
                PsiMethod derivedMethod = element;
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
          ArrayList sortedIDs = getSortedIDs(myFieldUsagesRequests);
          for (int i = 0; i < sortedIDs.size(); i++) {
            PsiField psiField = (PsiField)sortedIDs.get(i);
            final List<UsagesProcessor> processors = myFieldUsagesRequests.get(psiField);

            incrementJobDoneAmount(FIND_EXTERNAL_USAGES, RefUtil.getQualifiedName(refManager.getReference(psiField)));

            helper.processReferences(createReferenceProcessor(processors), psiField, searchScope, false);
          }

          myFieldUsagesRequests = null;
        }

        if (myClassUsagesRequests != null) {
          ArrayList sortedIDs = getSortedIDs(myClassUsagesRequests);
          for (int i = 0; i < sortedIDs.size(); i++) {
            PsiClass psiClass = (PsiClass)sortedIDs.get(i);
            final List<UsagesProcessor> processors = myClassUsagesRequests.get(psiClass);

            incrementJobDoneAmount(FIND_EXTERNAL_USAGES, psiClass.getQualifiedName());

            helper.processReferences(createReferenceProcessor(processors), psiClass, searchScope, false);
          }

          myClassUsagesRequests = null;
        }

        if (myMethodUsagesRequests != null) {
          ArrayList sortedIDs = getSortedIDs(myMethodUsagesRequests);
          for (int i = 0; i < sortedIDs.size(); i++) {
            PsiMethod psiMethod = (PsiMethod)sortedIDs.get(i);
            final List<UsagesProcessor> processors = myMethodUsagesRequests.get(psiMethod);

            incrementJobDoneAmount(FIND_EXTERNAL_USAGES, RefUtil.getQualifiedName(refManager.getReference(psiMethod)));

            helper.processReferencesIncludingOverriding(createReferenceProcessor(processors), psiMethod, searchScope);
          }

          myMethodUsagesRequests = null;
        }
      }
    }, null);
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

  private static ArrayList getSortedIDs(HashMap requests) {
    ArrayList result = new ArrayList();
    for (Iterator iterator = requests.keySet().iterator(); iterator.hasNext();) {
      PsiElement id = (PsiElement)iterator.next();
      result.add(id);
    }

    Collections.sort(result, new Comparator() {
      public int compare(Object o1, Object o2) {
        PsiElement i1 = (PsiElement)o1;
        PsiElement i2 = (PsiElement)o2;

        return i1.getContainingFile().getName().compareTo(i2.getContainingFile().getName());
      }
    });

    return result;
  }

  private PsiReferenceProcessor createReferenceProcessor(final List<UsagesProcessor> processors) {
    return new PsiReferenceProcessor() {
      public boolean execute(PsiReference reference) {
        AnalysisScope scope = getRefManager().getScope();
        if (scope.contains(reference.getElement()) ||
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

    InspectionResultsView view = new InspectionResultsView(myProject);
    InspectionTool[] tools = getCurrentProfile().getInspectionTools(myProject);
    if (!view.update(tools)) {
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

            InspectionTool[] tools = getCurrentProfile().getInspectionTools(myProject);
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
      for (int i = 0; i < requestors.length; i++) {
        InspectionTool requestor = requestors[i];
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
    for (int i = 0; i < tools.length; i++) {
      InspectionTool tool = tools[i];
      if (getCurrentProfile().isToolEnabled(HighlightDisplayKey.find(tool.getShortName()))) tool.initialize(this);
    }

    try {
      scope.accept(new PsiRecursiveElementVisitor() {
        public void visitReferenceExpression(PsiReferenceExpression expression) {
        }

        public void visitJavaFile(PsiJavaFile file) {
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

    for (int i = 0; i < tools.length; i++) {
      InspectionTool tool = tools[i];
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
    for (int i = 0; i < tools.length; i++) {
      InspectionTool tool = tools[i];
      if (getCurrentProfile().isToolEnabled(HighlightDisplayKey.find(tool.getShortName()))) {
        if (tool instanceof LocalInspectionToolWrapper) {
          LocalInspectionToolWrapper wrapper = (LocalInspectionToolWrapper)tool;
          localTools.add(wrapper);
          appendJobDescriptor(LOCAL_ANALYSIS);
        }
        else {
          JobDescriptor[] jobDescriptors = tool.getJobDescriptors();
          for (int j = 0; j < jobDescriptors.length; j++) {
            appendJobDescriptor(jobDescriptors[j]);
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

  public UIOptions getUIOptions() {
    return myUIOptions;
  }

  public class UIOptions implements JDOMExternalizable {
    public boolean AUTOSCROLL_TO_SOURCE = false;
    public float SPLITTER_PROPORTION = 0.5f;
    public final AutoScrollToSourceHandler myAutoScrollToSourceHandler;

    public UIOptions() {
      myAutoScrollToSourceHandler = new AutoScrollToSourceHandler(myProject) {
        protected boolean isAutoScrollMode() {
          return AUTOSCROLL_TO_SOURCE;
        }

        protected void setAutoScrollMode(boolean state) {
          AUTOSCROLL_TO_SOURCE = state;
        }
      };
    }

    public void readExternal(org.jdom.Element element) throws InvalidDataException {
      DefaultJDOMExternalizer.readExternal(this, element);
    }

    public void writeExternal(org.jdom.Element element) throws WriteExternalException {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }
  }

  private class CloseAction extends AnAction {
    private CloseAction() {
      super("Close", null, IconLoader.getIcon("/actions/cancel.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      close();
    }
  }

  public void close() {
    getContentManager().removeAllContents();
    cleanup();
  }

  public void addCommonActions(DefaultActionGroup group, InspectionResultsView view) {
    group.add(new CloseAction());
    group.add(createToggleAutoscrollAction());
    group.add(new RerunAction(view));
    group.add(new PreviousOccurenceToolbarAction(view.getOccurenceNavigator()));
    group.add(new NextOccurenceToolbarAction(view.getOccurenceNavigator()));
    group.add(new ExportHTMLAction());
    group.add(new HelpAction());
  }

  public ToggleAction createToggleAutoscrollAction() {
    return myUIOptions.myAutoScrollToSourceHandler.createToggleAction();
  }

  public void installAutoscrollHandler(JTree tree) {
    myUIOptions.myAutoScrollToSourceHandler.install(tree);
  }

  private void exportHTML() {
    ExportToHTMLDialog exportToHTMLDialog = new ExportToHTMLDialog(myProject);
    final ExportToHTMLSettings exportToHTMLSettings = ExportToHTMLSettings.getInstance(myProject);
    if (exportToHTMLSettings.OUTPUT_DIRECTORY == null) {
      exportToHTMLSettings.OUTPUT_DIRECTORY = PathManager.getHomePath() + File.separator + "exportToHTML";
    }
    exportToHTMLDialog.reset();
    exportToHTMLDialog.show();
    if (!exportToHTMLDialog.isOK()) {
      return;
    }
    exportToHTMLDialog.apply();

    final String outputDirectoryName = exportToHTMLSettings.OUTPUT_DIRECTORY;
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final Runnable exportRunnable = new Runnable() {
          public void run() {
            HTMLExportFrameMaker maker = new HTMLExportFrameMaker(outputDirectoryName, myProject);
            maker.start();
            try {
              myView.exportHTML(maker);
            }
            catch (ProcessCanceledException e) {
              // Do nothing here.
            }

            maker.done();
          }
        };

        if (!ApplicationManager.getApplication().runProcessWithProgressSynchronously(exportRunnable, "Generating HTML...", true, myProject)) return;

        if (exportToHTMLSettings.OPEN_IN_BROWSER) {
          BrowserUtil.launchBrowser(exportToHTMLSettings.OUTPUT_DIRECTORY + File.separator + "index.html");
        }
      }
    });
  }

  public void setLeftSplitterProportion(float proportion) {
    getUIOptions().SPLITTER_PROPORTION = proportion;
  }

  private static class HelpAction extends AnAction {
    private HelpAction() {
      super("Help", null, IconLoader.getIcon("/actions/help.png"));
    }

    public void actionPerformed(AnActionEvent event) {
      HelpManager.getInstance().invokeHelp(HELP_ID);
    }
  }

  private class ExportHTMLAction extends AnAction {
    public ExportHTMLAction() {
      super("Export HTML", null, IconLoader.getIcon("/actions/export.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      exportHTML();
    }
  }

  private class RerunAction extends AnAction {
    public RerunAction(JComponent comp) {
      super("Rerun Inspection", "Rerun Inspection", IconLoader.getIcon("/actions/refreshUsages.png"));
      registerCustomShortcutSet(CommonShortcuts.getRerun(), comp);
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myCurrentScope.isValid());
    }

    public void actionPerformed(AnActionEvent e) {
      rerun();
    }
  }

  public String getComponentName() {
    return "InspectionManager";
  }

  public void refreshViews() {
    InspectionTool[] tools = getCurrentProfile().getInspectionTools(myProject);
    myView.update(tools);
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
