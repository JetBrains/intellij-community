package com.intellij.tools;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.DefaultJavaProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.RunStrategy;
import com.intellij.execution.util.ExecutionErrorDialog;
import com.intellij.ide.macro.Macro;
import com.intellij.ide.macro.MacroManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;

public class Tool {
  @NonNls public final static String ACTION_ID_PREFIX = "Tool_";

  private String myName;
  private String myDescription;
  private String myGroup;
  private boolean myShownInMainMenu;
  private boolean myShownInEditor;
  private boolean myShownInProjectViews;
  private boolean myShownInSearchResultsPopup;
  private boolean myEnabled;

  private boolean myUseConsole;
  private boolean mySynchronizeAfterExecution;

  private String myWorkingDirectory;
  private String myProgram;
  private String myParameters;

  private ArrayList<FilterInfo> myOutputFilters = new ArrayList<FilterInfo>();

  public Tool() {
  }

  public String getName() {
    return myName;
  }

  public String getDescription() {
    return myDescription;
  }

  public String getGroup() {
    return myGroup;
  }

  public boolean isShownInMainMenu() {
    return myShownInMainMenu;
  }

  public boolean isShownInEditor() {
    return myShownInEditor;
  }

  public boolean isShownInProjectViews() {
    return myShownInProjectViews;
  }

  public boolean isShownInSearchResultsPopup() {
    return myShownInSearchResultsPopup;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  public boolean isUseConsole() {
    return myUseConsole;
  }

  public boolean synchronizeAfterExecution() {
    return mySynchronizeAfterExecution;
  }

  void setName(String name) {
    myName = name;
  }

  void setDescription(String description) {
    myDescription = description;
  }

  void setGroup(String group) {
    myGroup = group;
  }

  void setShownInMainMenu(boolean shownInMainMenu) {
    myShownInMainMenu = shownInMainMenu;
  }

  void setShownInEditor(boolean shownInEditor) {
    myShownInEditor = shownInEditor;
  }

  void setShownInProjectViews(boolean shownInProjectViews) {
    myShownInProjectViews = shownInProjectViews;
  }

  public void setShownInSearchResultsPopup(boolean shownInSearchResultsPopup) {
    myShownInSearchResultsPopup = shownInSearchResultsPopup;
  }

  void setUseConsole(boolean useConsole) {
    myUseConsole = useConsole;
  }

  public void setFilesSynchronizedAfterRun(boolean synchronizeAfterRun) {
    mySynchronizeAfterExecution = synchronizeAfterRun;
  }

  public String getWorkingDirectory() {
    return myWorkingDirectory;
  }

  public void setWorkingDirectory(String workingDirectory) {
    myWorkingDirectory = workingDirectory;
  }

  public String getProgram() {
    return myProgram;
  }

  public void setProgram(String program) {
    myProgram = program;
  }

  public String getParameters() {
    return myParameters;
  }

  public void setParameters(String parameters) {
    myParameters = parameters;
  }

  public void addOutputFilter(FilterInfo filter) {
    myOutputFilters.add(filter);
  }

  public void setOutputFilters(FilterInfo[] filters) {
    myOutputFilters = new ArrayList<FilterInfo>();
    for (int i = 0; i < filters.length; i++) {
      myOutputFilters.add(filters[i]);
    }
  }

  public FilterInfo[] getOutputFilters() {
    return myOutputFilters.toArray(new FilterInfo[myOutputFilters.size()]);
  }

  public void copyFrom(Tool source) {
    myName = source.myName;
    myDescription = source.myDescription;
    myGroup = source.myGroup;
    myShownInMainMenu = source.myShownInMainMenu;
    myShownInEditor = source.myShownInEditor;
    myShownInProjectViews = source.myShownInProjectViews;
    myShownInSearchResultsPopup = source.myShownInSearchResultsPopup;
    myEnabled = source.myEnabled;
    myUseConsole = source.myUseConsole;
    mySynchronizeAfterExecution = source.mySynchronizeAfterExecution;
    myWorkingDirectory = source.myWorkingDirectory;
    myProgram = source.myProgram;
    myParameters = source.myParameters;
    myOutputFilters = (ArrayList<FilterInfo>)source.myOutputFilters.clone();
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof Tool)) return false;
    Tool secondTool = (Tool)obj;

    Tool source = secondTool;

    return
      Comparing.equal(myName, source.myName) &&
      Comparing.equal(myDescription, source.myDescription) &&
      Comparing.equal(myGroup, source.myGroup) &&
      myShownInMainMenu == source.myShownInMainMenu &&
      myShownInEditor == source.myShownInEditor &&
      myShownInProjectViews == source.myShownInProjectViews &&
      myShownInSearchResultsPopup == source.myShownInSearchResultsPopup &&
      myEnabled == source.myEnabled &&
      myUseConsole == source.myUseConsole &&
      mySynchronizeAfterExecution == source.mySynchronizeAfterExecution &&
      Comparing.equal(myWorkingDirectory, source.myWorkingDirectory) &&
      Comparing.equal(myProgram, source.myProgram) &&
      Comparing.equal(myParameters, source.myParameters) &&
      Comparing.equal(myOutputFilters, source.myOutputFilters);
  }

  public String getActionId() {
    StringBuffer name = new StringBuffer(ACTION_ID_PREFIX);
    if (myGroup != null) {
      name.append(myGroup);
      name.append('_');
    }
    if (myName != null) {
      name.append(myName);
    }
    return name.toString();
  }

  public void execute(DataContext dataContext) {
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      return;
    }
    FileDocumentManager.getInstance().saveAllDocuments();
    try {
      if (isUseConsole()) {
        RunStrategy.getInstance().executeDefault(new ToolRunProfile(this, dataContext), dataContext);
      }
      else {
        GeneralCommandLine commandLine = createCommandLine(dataContext);
        if (commandLine == null) {
          return;
        }
        OSProcessHandler handler = new DefaultJavaProcessHandler(commandLine);
        handler.addProcessListener(new ToolProcessAdapter(project, synchronizeAfterExecution(), getName()));
        handler.startNotify();
        /*
        ContentManager contentManager = RunManager.getInstance(project).getViewProvider();
        ExecutionView.Descriptor descriptor = new ExecutionView.Descriptor(project, getName(), contentManager,
                                                                           ToolWindowId.RUN);
        descriptor.canBreak = false;
        Content contentToReuse = RunManager.getInstance(project).getContentToReuse();
        executionView = ExecutionView.openExecutionView(descriptor, contentToReuse, true, DefaultConsoleViewFactory.getInstance());
        executionView.addAction(new EditToolAction(executionView), 1);
        WindowManager.getInstance().getStatusBar(project).setInfo("External tool '" + getName() + "' started");
        if (executionView.enterCriticalSection()) {
          OSProcessHandler handler = commandLine.createProcessHandler();
          executionView.getConsoleView().print(handler.getCommandLine() + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
          executionView.setProcessHandler(handler);
          handler.addProcessListener(new MyProcessAdapter(executionView, project));
          // Add filters;
          for (int i = 0; i < myOutputFilters.size(); i++) {
            RegexpFilter filter = myOutputFilters.get(i);
            if (filter != null) {
              executionView.getConsoleView().addMessageFilter(filter);
            }
          }
          handler.startNotify();
        }
        */
      }
    }
    catch (ExecutionException ex) {
      ExecutionErrorDialog.show(ex, ToolsBundle.message("tools.process.start.error"), project);
    }
  }

  GeneralCommandLine createCommandLine(DataContext dataContext) {
    if (getWorkingDirectory() != null && getWorkingDirectory().trim().length() == 0) {
      setWorkingDirectory(null);
    }

    GeneralCommandLine commandLine = new GeneralCommandLine();
    try {
      String paramString = MacroManager.getInstance().expandMacrosInString(getParameters(), true, dataContext);
      String workingDir = MacroManager.getInstance().expandMacrosInString(getWorkingDirectory(), true, dataContext);
      String exePath = MacroManager.getInstance().expandMacrosInString(getProgram(), true, dataContext);

      commandLine.getParametersList().addParametersString(
        MacroManager.getInstance().expandMacrosInString(paramString, false, dataContext));
      commandLine.setWorkDirectory(MacroManager.getInstance().expandMacrosInString(workingDir, false, dataContext));
      exePath = MacroManager.getInstance().expandMacrosInString(exePath, false, dataContext);
      if (exePath == null) return null;
      commandLine.setExePath(exePath);
    }
    catch (Macro.ExecutionCancelledException e) {
      return null;
    }
    return commandLine;
  }

}
