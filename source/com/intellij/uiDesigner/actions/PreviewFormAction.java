package com.intellij.uiDesigner.actions;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionRegistry;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuidlerFactory;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.runners.RunStrategy;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootsTraversing;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.GuiEditor;
import com.intellij.uiDesigner.GuiEditorUtil;
import com.intellij.uiDesigner.compiler.CodeGenerator;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.uiDesigner.make.CopyResourcesUtil;
import com.intellij.uiDesigner.make.Form2ByteCodeCompiler;
import com.intellij.util.BcelUtils;
import com.intellij.util.PathsList;
import org.apache.bcel.util.ClassPath;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class PreviewFormAction extends AnAction{
  private final GuiEditor myEditor;
  /**
   * The problem is that this class is in a default package so it's not
   * import this class to refer
   */
  private static final String CLASS_TO_BIND_NAME = "FormPreviewFrame";

  public PreviewFormAction() {
    myEditor = null;
  }

  public PreviewFormAction(final GuiEditor editor) {
    copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIEW_FORM));
    myEditor = editor;
  }

  private GuiEditor getEditor(final DataContext context){
    return myEditor != null ? myEditor : GuiEditorUtil.getEditorFromContext(context);
  }

  public void actionPerformed(final AnActionEvent e) {
    final GuiEditor editor = getEditor(e.getDataContext());
    showPreviewFrame(editor.getModule(), editor.getFile(), e.getDataContext());
  }

  public void update(final AnActionEvent e) {
    final GuiEditor editor = getEditor(e.getDataContext());

    if(editor == null){
      e.getPresentation().setEnabled(false);
      return;
    }

    final VirtualFile file = editor.getFile();
    e.getPresentation().setEnabled(
      FileDocumentManager.getInstance().getDocument(file) != null &&
      FileTypeManager.getInstance().getFileTypeByFile(file) == StdFileTypes.GUI_DESIGNER_FORM
    );
  }

  private static void showPreviewFrame(final Module module, final VirtualFile formFile, final DataContext dataContext) {
    if (module == null) {
      throw new IllegalArgumentException("module cannot be null");
    }
    if (formFile == null) {
      throw new IllegalArgumentException("formFile cannot be null");
    }

    final String tempPath;
    try {
      final File tempDirectory = FileUtil.createTempDirectory("FormPreview", "");
      tempPath = tempDirectory.getAbsolutePath();

      CopyResourcesUtil.copyFormsRuntime(tempPath, true);
    }
    catch (IOException e) {
      Messages.showErrorDialog(
        module.getProject(),
        "Cannot preview form '" + formFile.getPath().replace('/', File.separatorChar) + "'\n" +
          "Reason: " + e.toString(),
        "Error"
      );
      return;
    }

    final PathsList sources = ProjectRootsTraversing.collectRoots(module, ProjectRootsTraversing.PROJECT_SOURCES);
    final String classPath =
      ProjectRootsTraversing.collectRoots(module, ProjectRootsTraversing.FULL_CLASSPATH_RECURSIVE).getPathsString() + File.pathSeparator +
      sources.getPathsString() + File.pathSeparator + /* resources bundles */
      tempPath;
    final ClassLoader loader = Form2ByteCodeCompiler.createClassLoader(classPath);

    com.intellij.util.BcelUtils.initBcel(new ClassPath(classPath));

    try {
      final Document doc = FileDocumentManager.getInstance().getDocument(formFile);
      final LwRootContainer rootContainer;
      try {
        rootContainer = Utils.getRootContainer(doc.getText(), new CompiledClassPropertiesProvider(loader));
      }
      catch (Exception e) {
        Messages.showErrorDialog(
          module.getProject(),
          "Cannot read form file '" + formFile.getPath().replace('/', File.separatorChar) + "'.\n" +
            "Reason: " + e.getMessage(),
          "Error"
        );
        return;
      }

      if (rootContainer.getComponentCount() == 0) {
        Messages.showErrorDialog(
          module.getProject(),
          "Cannot preview an empty form '" + formFile.getPath().replace('/', File.separatorChar) + "'\n",
          "Error"
        );
        return;
      }

      // 1. Prepare form to preview. We have to change container so that it has only one binding.
      rootContainer.setClassToBind(CLASS_TO_BIND_NAME);
      FormEditingUtil.iterate(
        rootContainer,
        new FormEditingUtil.ComponentVisitor<LwComponent>() {
          public boolean visit(final LwComponent iComponent) {
            iComponent.setBinding(null);
            return true;
          }
        }
      );
      if (rootContainer.getComponentCount() == 1) {
        ((LwComponent)rootContainer.getComponent(0)).setBinding("myComponent");
      }

      // 2. Copy previewer class and all its superclasses into TEMP directory and instrument it.
      try {
        final File tempFile = CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_NAME, true);
        CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_NAME + "$1", true);
        CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_NAME + "$MyWindowListener", true);
        CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_NAME + "$MyExitAction", true);
        CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_NAME + "$MyPackAction", true);
        CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_NAME + "$MySetLafAction", true);

        final CodeGenerator codeGenerator = new CodeGenerator(rootContainer, tempFile, loader);
        codeGenerator.patch();
        final String[] errors = codeGenerator.getErrors();
        if(errors.length != 0){
          Messages.showErrorDialog(
            module.getProject(),
            "Cannot preview form '" + formFile.getPath().replace('/', File.separatorChar) + "'\n" +
              "Reason: " + errors[0],
            "Error"
          );
          return;
        }
      }
      catch (Exception e) {
        Messages.showErrorDialog(
          module.getProject(),
          "Cannot preview form '" + formFile.getPath().replace('/', File.separatorChar) + "'\n" +
            "Reason: " + (e.getMessage() != null ? e.getMessage() : e.toString()),
          "Error"
        );
        return;
      }

      // 3. Now we are ready to launch Java process
      final JavaParameters parameters = new JavaParameters();
      parameters.getClassPath().add(tempPath);
      final List<String> paths = sources.getPathList();
      for (Iterator<String> it = paths.iterator(); it.hasNext();) {
        parameters.getClassPath().add(it.next());
      }
      try {
        parameters.configureByModule(module, JavaParameters.JDK_AND_CLASSES);
      }
      catch (CantRunException e) {
        Messages.showErrorDialog(
          module.getProject(),
          "Cannot preview form '" + formFile.getPath().replace('/', File.separatorChar) + "'\n" +
            "Reason: " + e.getMessage(),
          "Error"
        );
        return;
      }
      parameters.setMainClass("FormPreviewFrame");
      parameters.setWorkingDirectory(tempPath);

      try {
        JavaProgramRunner defaultRunner = ExecutionRegistry.getInstance().getDefaultRunner();
        RunStrategy.getInstance().execute(
          new MyRunProfile(module, parameters, formFile.getPresentableUrl() + " preview started"),
          dataContext,
          defaultRunner, null, null);
      }
      catch (ExecutionException e) {
        Messages.showErrorDialog(
          module.getProject(),
          "Cannot preview form '" + formFile.getPath().replace('/', File.separatorChar) + "'\nReason: " + e.getMessage(),
          "Error"
        );
        return;
      }
    }
    finally {
      BcelUtils.disposeBcel();
    }
  }

  private static final class MyRunProfile implements RunProfile {
    private final Module myModule;
    private final JavaParameters myParams;
    private final String myStatusbarMessage;

    public MyRunProfile(final Module module, final JavaParameters params, final String statusbarMessage) {
      myModule = module;
      myParams = params;
      myStatusbarMessage = statusbarMessage;
    }

    public RunProfileState getState(final DataContext context,
                                    final RunnerInfo runnerInfo,
                                    RunnerSettings runnerSettings,
                                    ConfigurationPerRunnerSettings configurationSettings) {
      final JavaCommandLineState state = new JavaCommandLineState(runnerSettings, configurationSettings) {
        protected JavaParameters createJavaParameters() {
          return myParams;
        }

        public ExecutionResult execute() throws ExecutionException {
          try {
            return super.execute();
          }
          finally {
            final Project project = myModule.getProject();
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                WindowManager.getInstance().getStatusBar(project).setInfo(myStatusbarMessage);
              }
            });
          }
        }
      };
      state.setConsoleBuilder(TextConsoleBuidlerFactory.getInstance().createBuilder(myModule.getProject()));
      return state;
    }

    public String getName() {
      return "Form Preview";
    }

    public void checkConfiguration() throws RuntimeConfigurationException {
    }

    public Module[] getModules() {
      return new Module[] {myModule};
    }
  }
}
