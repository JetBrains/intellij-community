// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.actions;

import com.intellij.CommonBundle;
import com.intellij.compiler.PsiClassWriter;
import com.intellij.compiler.impl.FileSetCompileScope;
import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.GuiFormFileType;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.compiler.FormErrorInfo;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.make.PreviewNestedFormLoader;
import com.intellij.util.PathsList;
import com.jgoodies.forms.layout.CellConstraints;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.java.CopyResourcesUtil;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public final class PreviewFormAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(PreviewFormAction.class);

  /**
   * The problem is that this class is in a default package so it's not
   * import this class to refer
   */
  private static final String CLASS_TO_BIND_NAME = "com.intellij.uiDesigner.FormPreviewFrame";
  private static final String CLASS_TO_BIND_RESOURCE_NAME = "com/intellij/uiDesigner/FormPreviewFrame";
  private static final @NonNls String RUNTIME_BUNDLE_PREFIX = "messages.RuntimeBundle";
  public static final @NonNls String PREVIEW_BINDING_FIELD = "myComponent";

  public static @NotNull InstrumentationClassFinder createClassFinder(URL @Nullable [] platformUrls, final @NotNull String classPath) {
    final ArrayList<URL> urls = new ArrayList<>();
    for (StringTokenizer tokenizer = new StringTokenizer(classPath, File.pathSeparator); tokenizer.hasMoreTokens();) {
      final String s = tokenizer.nextToken();
      try {
        urls.add(new File(s).toURI().toURL());
      }
      catch (Exception exc) {
        throw new RuntimeException(exc);
      }
    }
    URL[] zero = new URL[0];
    return new InstrumentationClassFinder(platformUrls == null ? zero : platformUrls, urls.toArray(zero));
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    final GuiEditor editor = FormEditingUtil.getActiveEditor(e.getDataContext());
    if (editor != null) {
      showPreviewFrame(editor.getModule(), editor.getFile(), editor.getStringDescriptorLocale());
    }
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    GuiEditor editor = FormEditingUtil.getActiveEditor(e.getDataContext());

    if (editor == null) {
      e.getPresentation().setVisible(false);
      return;
    }

    final VirtualFile file = editor.getFile();
    e.getPresentation().setVisible(
      FileDocumentManager.getInstance().getDocument(file) != null &&
      FileTypeRegistry.getInstance().isFileOfType(file, GuiFormFileType.INSTANCE)
    );
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT ;
  }

  private static void showPreviewFrame(final @NotNull Module module, final @NotNull VirtualFile formFile,
                                       final @Nullable Locale stringDescriptorLocale) {
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

    URL[] platformUrls = null;
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk != null && sdk.getHomePath() != null && JavaSdk.getInstance().isOfVersionOrHigher(sdk, JavaSdkVersion.JDK_1_9)) {
      try {
        platformUrls = new URL[]{InstrumentationClassFinder.createJDKPlatformUrl(sdk.getHomePath())};
      }
      catch (MalformedURLException ignore) {
      }
    }

    final PathsList sources = OrderEnumerator.orderEntries(module).withoutSdk().withoutLibraries().withoutDepModules().getSourcePathsList();
    final String classPath = OrderEnumerator.orderEntries(module).recursively().getPathsList().getPathsString() + File.pathSeparator +
      sources.getPathsString() + File.pathSeparator + /* resources bundles */
      tempPath;
    final InstrumentationClassFinder finder = createClassFinder(platformUrls, classPath);

    try {
      final Document doc = FileDocumentManager.getInstance().getDocument(formFile);
      final LwRootContainer rootContainer;
      try {
        rootContainer = Utils.getRootContainer(doc.getText(), new CompiledClassPropertiesProvider(finder.getLoader()));
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
        PreviewNestedFormLoader nestedFormLoader = new PreviewNestedFormLoader(module, tempPath, finder);

        final File tempFile = CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_RESOURCE_NAME, true);
        //CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_NAME + "$1", true);
        CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_RESOURCE_NAME + "$MyExitAction", true);
        CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_RESOURCE_NAME + "$MyPackAction", true);
        CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_RESOURCE_NAME + "$MySetLafAction", true);

        Locale locale = Locale.getDefault();
        if (!locale.getCountry().isEmpty() && !locale.getLanguage().isEmpty()) {
          CopyResourcesUtil.copyProperties(tempPath, RUNTIME_BUNDLE_PREFIX + "_" + locale.getLanguage() +
                                                     "_" + locale.getCountry() + PropertiesFileType.DOT_DEFAULT_EXTENSION);
        }
        if (!locale.getLanguage().isEmpty()) {
          CopyResourcesUtil.copyProperties(tempPath, RUNTIME_BUNDLE_PREFIX + "_" + locale.getLanguage() + PropertiesFileType.DOT_DEFAULT_EXTENSION);
        }
        CopyResourcesUtil.copyProperties(tempPath, RUNTIME_BUNDLE_PREFIX + "_" + locale.getLanguage() + PropertiesFileType.DOT_DEFAULT_EXTENSION);
        CopyResourcesUtil.copyProperties(tempPath, RUNTIME_BUNDLE_PREFIX + PropertiesFileType.DOT_DEFAULT_EXTENSION);

        final AsmCodeGenerator codeGenerator = new AsmCodeGenerator(
          rootContainer, finder, nestedFormLoader, true, new PsiClassWriter(module)
        );
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
      final HashSet<String> bundleSet = new HashSet<>();
      FormEditingUtil.iterateStringDescriptors(
        rootContainer,
        new FormEditingUtil.StringDescriptorVisitor<>() {
          @Override
          public boolean visit(final IComponent component, final StringDescriptor descriptor) {
            if (descriptor.getBundleName() != null) {
              bundleSet.add(descriptor.getDottedBundleName());
            }
            return true;
          }
        });

      if (!bundleSet.isEmpty()) {
        HashSet<VirtualFile> virtualFiles = new HashSet<>();
        HashSet<Module> modules = new HashSet<>();
        PropertiesReferenceManager manager = PropertiesReferenceManager.getInstance(module.getProject());
        for(String bundleName: bundleSet) {
          for(PropertiesFile propFile: manager.findPropertiesFiles(module, bundleName)) {
            virtualFiles.add(propFile.getVirtualFile());
            final Module moduleForFile = ModuleUtil.findModuleForFile(propFile.getVirtualFile(), module.getProject());
            if (moduleForFile != null) {
              modules.add(moduleForFile);
            }
          }
        }
        FileSetCompileScope scope = new FileSetCompileScope(virtualFiles, modules.toArray(Module.EMPTY_ARRAY));

        CompilerManager.getInstance(module.getProject()).make(scope, new CompileStatusNotification() {
          @Override
          public void finished(boolean aborted, int errors, int warnings, final @NotNull CompileContext compileContext) {
            if (!aborted && errors == 0) {
              runPreviewProcess(tempPath, sources, module, formFile, stringDescriptorLocale);
            }
          }
        });
      }
      else {
        runPreviewProcess(tempPath, sources, module, formFile, stringDescriptorLocale);
      }
    }
    finally {
      finder.releaseResources();
    }
  }

  public static void setPreviewBindings(final LwRootContainer rootContainer, final String classToBindName) {
    // 1. Prepare form to preview. We have to change container so that it has only one binding.
    rootContainer.setClassToBind(classToBindName);
    FormEditingUtil.iterate(
      rootContainer,
      new FormEditingUtil.ComponentVisitor<LwComponent>() {
        @Override
        public boolean visit(final LwComponent iComponent) {
          iComponent.setBinding(null);
          return true;
        }
      }
    );
    if (rootContainer.getComponentCount() == 1) {
      ((LwComponent)rootContainer.getComponent(0)).setBinding(PREVIEW_BINDING_FIELD);
    }
  }

  private static void runPreviewProcess(final String tempPath, final PathsList sources, final Module module, final VirtualFile formFile,
                                        final @Nullable Locale stringDescriptorLocale) {
    // 3. Now we are ready to launch Java process
    final JavaParameters parameters = new JavaParameters();
    parameters.getClassPath().add(tempPath);
    parameters.getClassPath().add(PathManager.getJarPathForClass(CellConstraints.class));
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
    parameters.setMainClass(CLASS_TO_BIND_NAME);
    parameters.setWorkingDirectory(tempPath);
    if (stringDescriptorLocale != null && !stringDescriptorLocale.getDisplayName().isEmpty()) {
      parameters.getVMParametersList().add("-Duser.language=" + stringDescriptorLocale.getLanguage());
    }

    try {
      RunProfile profile = new MyRunProfile(module, parameters, tempPath,
                                            UIDesignerBundle.message("progress.preview.started", formFile.getPresentableUrl()));
      ExecutionEnvironmentBuilder.create(module.getProject(), DefaultRunExecutor.getRunExecutorInstance(), profile).buildAndExecute();
    }
    catch (ExecutionException e) {
      Messages.showErrorDialog(
        module.getProject(),
        UIDesignerBundle.message("error.cannot.preview.form", formFile.getPath().replace('/', File.separatorChar), e.getMessage()),
        CommonBundle.getErrorTitle()
      );
    }
  }

  private static final class MyRunProfile implements ModuleRunProfile {
    private final Module myModule;
    private final JavaParameters myParams;
    private final String myTempPath;
    private final @Nls String myStatusbarMessage;

    MyRunProfile(final Module module, final JavaParameters params, final String tempPath, final @Nls String statusbarMessage) {
      myModule = module;
      myParams = params;
      myTempPath = tempPath;
      myStatusbarMessage = statusbarMessage;
    }

    @Override
    public Icon getIcon() {
      return null;
    }

    @Override
    public RunProfileState getState(final @NotNull Executor executor, final @NotNull ExecutionEnvironment env) throws ExecutionException {
      return new JavaCommandLineState(env) {
        @Override
        protected JavaParameters createJavaParameters() {
          return myParams;
        }

        @Override
        public @NotNull ExecutionResult execute(final @NotNull Executor executor, final @NotNull ProgramRunner<?> runner) throws ExecutionException {
          try {
            ExecutionResult executionResult = super.execute(executor, runner);
            executionResult.getProcessHandler().addProcessListener(new ProcessListener() {
              @Override
              public void processTerminated(@NotNull ProcessEvent event) {
                FileUtil.asyncDelete(new File(myTempPath));
              }
            });
            return executionResult;
          }
          finally {
            final Project project = myModule.getProject();
            SwingUtilities.invokeLater(() -> WindowManager.getInstance().getStatusBar(project).setInfo(myStatusbarMessage));
          }
        }
      };
    }

    @Override
    public @NotNull String getName() {
      return UIDesignerBundle.message("title.form.preview");
    }

    @Override
    public Module @NotNull [] getModules() {
      return new Module[] {myModule};
    }
  }
}
