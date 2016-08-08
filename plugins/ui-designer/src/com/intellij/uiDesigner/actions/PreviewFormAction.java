/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.actions;

import com.intellij.CommonBundle;
import com.intellij.compiler.PsiClassWriter;
import com.intellij.compiler.impl.FileSetCompileScope;
import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.compiler.FormErrorInfo;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.make.PreviewNestedFormLoader;
import com.intellij.util.PathsList;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.java.CopyResourcesUtil;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

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
  @NonNls public static final String PREVIEW_BINDING_FIELD = "myComponent";

  @NotNull
  public static InstrumentationClassFinder createClassFinder(@NotNull final String classPath){
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
    return new InstrumentationClassFinder(urls.toArray(new URL[urls.size()]));
  }

  public void actionPerformed(final AnActionEvent e) {
    final GuiEditor editor = FormEditingUtil.getActiveEditor(e.getDataContext());
    if (editor != null) {
      showPreviewFrame(editor.getModule(), editor.getFile(), editor.getStringDescriptorLocale());
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
      file.getFileType() == StdFileTypes.GUI_DESIGNER_FORM
    );
  }

  private static void showPreviewFrame(@NotNull final Module module, @NotNull final VirtualFile formFile,
                                       @Nullable final Locale stringDescriptorLocale) {
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

    final PathsList sources = OrderEnumerator.orderEntries(module).withoutSdk().withoutLibraries().withoutDepModules().getSourcePathsList();
    final String classPath = OrderEnumerator.orderEntries(module).recursively().getPathsList().getPathsString() + File.pathSeparator +
      sources.getPathsString() + File.pathSeparator + /* resources bundles */
      tempPath;
    final InstrumentationClassFinder finder = createClassFinder(classPath);

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

        final File tempFile = CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_NAME, true);
        //CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_NAME + "$1", true);
        CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_NAME + "$MyExitAction", true);
        CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_NAME + "$MyPackAction", true);
        CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_NAME + "$MySetLafAction", true);

        Locale locale = Locale.getDefault();
        if (locale.getCountry().length() > 0 && locale.getLanguage().length() > 0) {
          CopyResourcesUtil.copyProperties(tempPath, RUNTIME_BUNDLE_PREFIX + "_" + locale.getLanguage() +
                                                     "_" + locale.getCountry() + PropertiesFileType.DOT_DEFAULT_EXTENSION);
        }
        if (locale.getLanguage().length() > 0) {
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
        new FormEditingUtil.StringDescriptorVisitor<IComponent>() {
          public boolean visit(final IComponent component, final StringDescriptor descriptor) {
            if (descriptor.getBundleName() != null) {
              bundleSet.add(descriptor.getDottedBundleName());
            }
            return true;
          }
        });

      if (bundleSet.size() > 0) {
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
        FileSetCompileScope scope = new FileSetCompileScope(virtualFiles, modules.toArray(new Module[modules.size()]));

        CompilerManager.getInstance(module.getProject()).make(scope, new CompileStatusNotification() {
          public void finished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
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
                                        @Nullable final Locale stringDescriptorLocale) {
    // 3. Now we are ready to launch Java process
    final JavaParameters parameters = new JavaParameters();
    parameters.getClassPath().add(tempPath);
    parameters.getClassPath().add(PathManager.findFileInLibDirectory("jgoodies-forms.jar").getAbsolutePath());
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
    if (stringDescriptorLocale != null && stringDescriptorLocale.getDisplayName().length() > 0) {
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
    private final String myStatusbarMessage;

    public MyRunProfile(final Module module, final JavaParameters params, final String tempPath, final String statusbarMessage) {
      myModule = module;
      myParams = params;
      myTempPath = tempPath;
      myStatusbarMessage = statusbarMessage;
    }

    public Icon getIcon() {
      return null;
    }

    public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
      return new JavaCommandLineState(env) {
        protected JavaParameters createJavaParameters() {
          return myParams;
        }

        public ExecutionResult execute(@NotNull final Executor executor, @NotNull final ProgramRunner runner) throws ExecutionException {
          try {
            ExecutionResult executionResult = super.execute(executor, runner);
            executionResult.getProcessHandler().addProcessListener(new ProcessAdapter() {
              @Override
              public void processTerminated(ProcessEvent event) {
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

    public String getName() {
      return UIDesignerBundle.message("title.form.preview");
    }

    @NotNull
    public Module[] getModules() {
      return new Module[] {myModule};
    }
  }
}
