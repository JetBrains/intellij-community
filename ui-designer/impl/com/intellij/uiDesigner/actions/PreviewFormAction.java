package com.intellij.uiDesigner.actions;

import com.intellij.CommonBundle;
import com.intellij.compiler.impl.FileSetCompileScope;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionRegistry;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuidlerFactory;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.runners.RunStrategy;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootsTraversing;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.compiler.FormErrorInfo;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.make.CopyResourcesUtil;
import com.intellij.uiDesigner.make.Form2ByteCodeCompiler;
import com.intellij.uiDesigner.make.PreviewNestedFormLoader;
import com.intellij.util.PathsList;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class PreviewFormAction extends AnAction{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.actions.PreviewFormAction");

  /**
   * The problem is that this class is in a default package so it's not
   * import this class to refer
   */
  private static final String CLASS_TO_BIND_NAME = "FormPreviewFrame";
  @NonNls private static final String RUNTIME_BUNDLE_PREFIX = "RuntimeBundle";
  @NonNls private static final String RUNTIME_BUNDLE_EXTENSION = ".properties";
  @NonNls public static final String PREVIEW_BINDING_FIELD = "myComponent";

  public void actionPerformed(final AnActionEvent e) {
    final GuiEditor editor = FormEditingUtil.getActiveEditor(e.getDataContext());
    if (editor != null) {
      showPreviewFrame(editor.getModule(), editor.getFile(), e.getDataContext(), editor.getStringDescriptorLocale());
    }
  }

  public void update(final AnActionEvent e) {
    final GuiEditor editor = FormEditingUtil.getActiveEditor(e.getDataContext());

    if(editor == null){
      e.getPresentation().setVisible(false);
      return;
    }

    final VirtualFile file = editor.getFile();
    e.getPresentation().setVisible(
      FileDocumentManager.getInstance().getDocument(file) != null &&
      FileTypeManager.getInstance().getFileTypeByFile(file) == StdFileTypes.GUI_DESIGNER_FORM
    );
  }

  private static void showPreviewFrame(@NotNull final Module module, @NotNull final VirtualFile formFile,
                                       final DataContext dataContext, final Locale stringDescriptorLocale) {
    final String tempPath;
    try {
      final File tempDirectory = FileUtil.createTempDirectory("FormPreview", "");
      tempPath = tempDirectory.getAbsolutePath();

      CopyResourcesUtil.copyFormsRuntime(tempPath, true);
    }
    catch (IOException e) {
      Messages.showErrorDialog(
        module.getProject(),
        UIDesignerBundle.message("error.cannot.preview.form", formFile.getPath().replace('/', File.separatorChar), e.toString()),
        CommonBundle.getErrorTitle()
      );
      return;
    }

    final PathsList sources = ProjectRootsTraversing.collectRoots(module, ProjectRootsTraversing.PROJECT_SOURCES);
    final String classPath =
      ProjectRootsTraversing.collectRoots(module, ProjectRootsTraversing.FULL_CLASSPATH_RECURSIVE).getPathsString() + File.pathSeparator +
      sources.getPathsString() + File.pathSeparator + /* resources bundles */
      tempPath;
    final ClassLoader loader = Form2ByteCodeCompiler.createClassLoader(classPath);

    final Document doc = FileDocumentManager.getInstance().getDocument(formFile);
    final LwRootContainer rootContainer;
    try {
      rootContainer = Utils.getRootContainer(doc.getText(), new CompiledClassPropertiesProvider(loader));
    }
    catch (Exception e) {
      Messages.showErrorDialog(
        module.getProject(),
        UIDesignerBundle.message("error.cannot.read.form", formFile.getPath().replace('/', File.separatorChar), e.getMessage()),
        CommonBundle.getErrorTitle()
      );
      return;
    }

    if (rootContainer.getComponentCount() == 0) {
      Messages.showErrorDialog(
        module.getProject(),
        UIDesignerBundle.message("error.cannot.preview.empty.form", formFile.getPath().replace('/', File.separatorChar)),
        CommonBundle.getErrorTitle()
      );
      return;
    }

    setPreviewBindings(rootContainer, CLASS_TO_BIND_NAME);

    // 2. Copy previewer class and all its superclasses into TEMP directory and instrument it.
    try {
      PreviewNestedFormLoader nestedFormLoader = new PreviewNestedFormLoader(module, tempPath, loader);

      final File tempFile = CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_NAME, true);
      //CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_NAME + "$1", true);
      CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_NAME + "$MyExitAction", true);
      CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_NAME + "$MyPackAction", true);
      CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_NAME + "$MySetLafAction", true);

      Locale locale = Locale.getDefault();
      if (locale.getCountry().length() > 0 && locale.getLanguage().length() > 0) {
        CopyResourcesUtil.copyProperties(tempPath, RUNTIME_BUNDLE_PREFIX + "_" + locale.getLanguage() +
                                                   "_" + locale.getCountry() + RUNTIME_BUNDLE_EXTENSION);
      }
      if (locale.getLanguage().length() > 0) {
        CopyResourcesUtil.copyProperties(tempPath, RUNTIME_BUNDLE_PREFIX + "_" + locale.getLanguage() + RUNTIME_BUNDLE_EXTENSION);
      }
      CopyResourcesUtil.copyProperties(tempPath, RUNTIME_BUNDLE_PREFIX + "_" + locale.getLanguage() + RUNTIME_BUNDLE_EXTENSION);
      CopyResourcesUtil.copyProperties(tempPath, RUNTIME_BUNDLE_PREFIX + RUNTIME_BUNDLE_EXTENSION);

      final AsmCodeGenerator codeGenerator = new AsmCodeGenerator(rootContainer, loader, nestedFormLoader, true);
      codeGenerator.patchFile(tempFile);
      final FormErrorInfo[] errors = codeGenerator.getErrors();
      if(errors.length != 0){
        Messages.showErrorDialog(
          module.getProject(),
          UIDesignerBundle.message("error.cannot.preview.form",
                                   formFile.getPath().replace('/', File.separatorChar),
                                   errors[0].getErrorMessage()),
          CommonBundle.getErrorTitle()
        );
        return;
      }
    }
    catch (Exception e) {
      LOG.debug(e);
      Messages.showErrorDialog(
        module.getProject(),
        UIDesignerBundle.message("error.cannot.preview.form", formFile.getPath().replace('/', File.separatorChar),
                                 e.getMessage() != null ? e.getMessage() : e.toString()),
        CommonBundle.getErrorTitle()
      );
      return;
    }

    // 2.5. Copy up-to-date properties files to the output directory.
    final HashSet<String> bundleSet = new HashSet<String>();
    FormEditingUtil.iterateStringDescriptors(
      rootContainer,
      new FormEditingUtil.StringDescriptorVisitor<IComponent>() {
        public boolean visit(final IComponent component, final StringDescriptor descriptor) {
          if (descriptor.getBundleName() != null) {
            bundleSet.add(descriptor.getDottedBundleName());
          }
          return true;
        }
      });

    if (bundleSet.size() > 0) {
      HashSet<VirtualFile> virtualFiles = new HashSet<VirtualFile>();
      HashSet<Module> modules = new HashSet<Module>();
      PropertiesReferenceManager manager = PropertiesReferenceManager.getInstance(module.getProject());
      for(String bundleName: bundleSet) {
        for(PropertiesFile propFile: manager.findPropertiesFiles(module, bundleName)) {
          virtualFiles.add(propFile.getVirtualFile());
          modules.add(VfsUtil.getModuleForFile(module.getProject(), propFile.getVirtualFile()));
        }
      }
      FileSetCompileScope scope = new FileSetCompileScope(virtualFiles.toArray(new VirtualFile[] {}),
                                                          modules.toArray(new Module[] {}));

      CompilerManager.getInstance(module.getProject()).make(scope, new CompileStatusNotification() {
        public void finished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
          if (!aborted && errors == 0) {
            runPreviewProcess(tempPath, sources, module, formFile, dataContext, stringDescriptorLocale);
          }
        }
      });
    }
    else {
      runPreviewProcess(tempPath, sources, module, formFile, dataContext, stringDescriptorLocale);
    }
  }

  public static void setPreviewBindings(final LwRootContainer rootContainer, final String classToBindName) {
    // 1. Prepare form to preview. We have to change container so that it has only one binding.
    rootContainer.setClassToBind(classToBindName);
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
      //noinspection HardCodedStringLiteral
      ((LwComponent)rootContainer.getComponent(0)).setBinding(PREVIEW_BINDING_FIELD);
    }
  }

  private static void runPreviewProcess(final String tempPath, final PathsList sources, final Module module, final VirtualFile formFile,
                                        final DataContext dataContext, final Locale stringDescriptorLocale) {
    // 3. Now we are ready to launch Java process
    final JavaParameters parameters = new JavaParameters();
    parameters.getClassPath().add(tempPath);
    parameters.getClassPath().add(tempPath + "/jgoodies-forms.jar");
    final List<String> paths = sources.getPathList();
    for (final String path : paths) {
      parameters.getClassPath().add(path);
    }
    try {
      parameters.configureByModule(module, JavaParameters.JDK_AND_CLASSES);
    }
    catch (CantRunException e) {
      Messages.showErrorDialog(
        module.getProject(),
        UIDesignerBundle.message("error.cannot.preview.form", formFile.getPath().replace('/', File.separatorChar), e.getMessage()),
        CommonBundle.getErrorTitle()
      );
      return;
    }
    parameters.setMainClass("FormPreviewFrame");
    parameters.setWorkingDirectory(tempPath);
    if (stringDescriptorLocale.getDisplayName().length() > 0) {
      parameters.getVMParametersList().add("-Duser.language=" + stringDescriptorLocale.getLanguage());
    }

    try {
      JavaProgramRunner defaultRunner = ExecutionRegistry.getInstance().getDefaultRunner();
      RunStrategy.getInstance().execute(
        new MyRunProfile(module, parameters, UIDesignerBundle.message("progress.preview.started", formFile.getPresentableUrl())),
        new DataContext() {   // IDEADEV-3596
          public Object getData(String dataId) {
            if (dataId.equals(DataConstants.PROJECT)) {
              return module.getProject();
            }
            return dataContext.getData(dataId);
          }
        },
        defaultRunner, null, null);
    }
    catch (ExecutionException e) {
      Messages.showErrorDialog(
        module.getProject(),
        UIDesignerBundle.message("error.cannot.preview.form", formFile.getPath().replace('/', File.separatorChar), e.getMessage()),
        CommonBundle.getErrorTitle()
      );
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
      return UIDesignerBundle.message("title.form.preview");
    }

    public void checkConfiguration() throws RuntimeConfigurationException {
    }

    public Module[] getModules() {
      return new Module[] {myModule};
    }
  }
}
