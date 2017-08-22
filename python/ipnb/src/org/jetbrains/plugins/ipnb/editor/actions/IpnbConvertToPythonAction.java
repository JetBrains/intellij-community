package org.jetbrains.plugins.ipnb.editor.actions;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.GuiUtils;
import com.jetbrains.python.sdk.PythonSdkType;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;

import java.util.ArrayList;

public class IpnbConvertToPythonAction extends AnAction {
  private IpnbFileEditor myFileEditor = null;

  // for action registered in xml
  public IpnbConvertToPythonAction() {
    super(PythonIcons.Python.Python);
  }

  // for action button on editor toolbar
  public IpnbConvertToPythonAction(@NotNull IpnbFileEditor fileEditor) {
    super("Convert to Python Script", "Convert to Python Script", PythonIcons.Python.Python);
    myFileEditor = fileEditor;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    if (project == null) {
      return;
    }

    if (myFileEditor != null) {
      VirtualFile virtualFile = myFileEditor.getVirtualFile();
      convertToPythonScript(project, virtualFile);
    }
    else {
      final DataContext context = event.getDataContext();
      VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(context);
      if (virtualFile == null) return;
      convertToPythonScript(project, virtualFile);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    if (myFileEditor != null) {
      e.getPresentation().setEnabledAndVisible(true);
      return;
    }

    final DataContext context = e.getDataContext();
    VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(context);
    if (virtualFile != null && "ipynb".equals(virtualFile.getExtension())) {
      e.getPresentation().setEnabledAndVisible(true);
    }
    else {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }

  private static void convertToPythonScript(@NotNull final Project project,
                                           @NotNull final VirtualFile virtualFile) {
    Module module = ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(virtualFile);
    if (module == null) return;

    final Sdk sdk = PythonSdkType.findPythonSdk(module);
    if (sdk == null || PythonSdkType.isInvalid(sdk)) {
      return;
    }
    final String homePath = sdk.getHomePath();
    if (homePath == null) {
      return;
    }

    final ArrayList<String> parameters = Lists.newArrayList(homePath);
    addJupyterRunner(homePath, parameters);
    parameters.add("--to");
    parameters.add("script");
    parameters.add(virtualFile.getPath());

    final String baseDir = virtualFile.getParent().getPath();
    final GeneralCommandLine commandLine = new GeneralCommandLine(parameters).withWorkDirectory(baseDir);

    try {
      final KillableColoredProcessHandler processHandler = new KillableColoredProcessHandler(commandLine);
      processHandler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          VfsUtil.markDirtyAndRefresh(true, false, true, virtualFile.getParent());
        }
      });
      processHandler.setShouldDestroyProcessRecursively(true);
      GuiUtils.invokeLaterIfNeeded(() -> {
        final RunContentExecutor executor = new RunContentExecutor(project, processHandler);
        executor.withActivateToolWindow(false);
        executor.run();
      }, ModalityState.defaultModalityState());
    }
    catch (ExecutionException ignored) {
    }
  }

  private static void addJupyterRunner(@NotNull final String homePath, ArrayList<String> parameters) {
    String nbconvert = PythonSdkType.getExecutablePath(homePath, "jupyter-nbconvert");
    if (nbconvert != null) {
      parameters.add(nbconvert);
    }
    else {
      nbconvert = PythonSdkType.getExecutablePath(homePath, "jupyter");
      parameters.add(nbconvert);
      parameters.add("nbconvert");
    }
  }
}
