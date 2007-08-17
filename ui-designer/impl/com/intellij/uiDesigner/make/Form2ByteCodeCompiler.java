package com.intellij.uiDesigner.make;

import com.intellij.compiler.PsiClassWriter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompilerPathsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootsTraversing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.uiDesigner.GuiDesignerConfiguration;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.compiler.FormErrorInfo;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassWriter;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;

public final class Form2ByteCodeCompiler implements ClassInstrumentingCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.make.Form2ByteCodeCompiler");

  private final Project myProject;

  public Form2ByteCodeCompiler(final Project project) {
    myProject = project;
  }

  @NotNull
  public String getDescription() {
    return UIDesignerBundle.message("component.gui.designer.form.to.bytecode.compiler");
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  @NotNull
  public static URLClassLoader createClassLoader(@NotNull final String classPath){
    final ArrayList<URL> urls = new ArrayList<URL>();
    for (StringTokenizer tokenizer = new StringTokenizer(classPath, File.pathSeparator); tokenizer.hasMoreTokens();) {
      final String s = tokenizer.nextToken();
      try {
        urls.add(new File(s).toURI().toURL());
      }
      catch (Exception exc) {
        throw new RuntimeException(exc);
      }
    }
    return new URLClassLoader(urls.toArray(new URL[urls.size()]), null);
  }

  @NotNull
  public FileProcessingCompiler.ProcessingItem[] getProcessingItems(final CompileContext context) {
    if (!GuiDesignerConfiguration.getInstance(myProject).INSTRUMENT_CLASSES) {
      return ProcessingItem.EMPTY_ARRAY;
    }

    final ArrayList<FileProcessingCompiler.ProcessingItem> items = new ArrayList<FileProcessingCompiler.ProcessingItem>();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final CompileScope scope = context.getCompileScope();
        final CompileScope projectScope = context.getProjectCompileScope();

        final VirtualFile[] formFiles = projectScope.getFiles(StdFileTypes.GUI_DESIGNER_FORM, true);
        if (formFiles.length==0) return;
        final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
        final BindingsCache bindingsCache = new BindingsCache(myProject);
        final VirtualFile[] outputDirectories = CompilerPathsEx.getOutputDirectories(
          ModuleManager.getInstance(myProject).getSortedModules()
        );

        final HashMap<Module, ArrayList<VirtualFile>> module2formFiles = sortByModules(formFiles);

        try {
          for (final Module module : module2formFiles.keySet()) {
            final HashMap<String, VirtualFile> class2form = new HashMap<String, VirtualFile>();

            final ArrayList<VirtualFile> list = module2formFiles.get(module);
            for (final VirtualFile formFile : list) {
              if (compilerManager.isExcludedFromCompilation(formFile)) {
                continue;
              }

              final String classToBind;
              try {
                classToBind = bindingsCache.getBoundClassName(formFile);
              }
              catch (AlienFormFileException e) {
                // ignore non-IDEA forms
                continue;
              }
              catch (Exception e) {
                addMessage(context, UIDesignerBundle.message("error.cannot.process.form.file", e), formFile, CompilerMessageCategory.ERROR);
                continue;
              }

              if (classToBind == null) {
                continue;
              }

              final VirtualFile classFile = findFile(outputDirectories, classToBind, module);
              if (classFile == null) {
                if (scope.belongs(formFile.getUrl())) {
                  addMessage(context, UIDesignerBundle.message("error.class.to.bind.does.not.exist", classToBind), formFile,
                             CompilerMessageCategory.ERROR);
                }
                continue;
              }

              final VirtualFile sourceFile = FormCompilerManager.findSourceFile(context, formFile, classToBind);

              final boolean inScope = (sourceFile == null) ?
                                      scope.belongs(formFile.getUrl()) :
                                      scope.belongs(sourceFile.getUrl()) || scope.belongs(formFile.getUrl());

              final VirtualFile alreadyProcessedForm = class2form.get(classToBind);
              if (alreadyProcessedForm != null) {
                if (inScope) {
                  addMessage(
                    context,
                    UIDesignerBundle.message("error.duplicate.bind",
                                             classToBind, alreadyProcessedForm.getPresentableUrl()),
                    formFile, CompilerMessageCategory.ERROR);
                }
                continue;
              }
              class2form.put(classToBind, formFile);

              if (!inScope) {
                continue;
              }

              final ProcessingItem item = new MyInstrumentationItem(classFile, formFile);
              items.add(item);
            }
          }
        }
        finally {
          bindingsCache.save();
        }
      }
    });

    return items.toArray(new FileProcessingCompiler.ProcessingItem[items.size()]);
  }

  private HashMap<Module, ArrayList<VirtualFile>> sortByModules(final VirtualFile[] formFiles) {
    final HashMap<Module, ArrayList<VirtualFile>> module2formFiles = new HashMap<Module,ArrayList<VirtualFile>>();
    for (final VirtualFile formFile : formFiles) {
      final Module module = VfsUtil.getModuleForFile(myProject, formFile);
      if (module != null) {
        ArrayList<VirtualFile> list = module2formFiles.get(module);
        if (list == null) {
          list = new ArrayList<VirtualFile>();
          module2formFiles.put(module, list);
        }
        list.add(formFile);
      }
      else {
        // todo[anton] handle somehow
      }
    }
    return module2formFiles;
  }

  private HashMap<Module, ArrayList<MyInstrumentationItem>> sortByModules(final FileProcessingCompiler.ProcessingItem[] items) {
    final HashMap<Module, ArrayList<MyInstrumentationItem>> module2formFiles = new HashMap<Module,ArrayList<MyInstrumentationItem>>();
    for (ProcessingItem item1 : items) {
      final MyInstrumentationItem item = (MyInstrumentationItem)item1;
      final VirtualFile formFile = item.getFormFile();

      final Module module = VfsUtil.getModuleForFile(myProject, formFile);
      if (module != null) {
        ArrayList<MyInstrumentationItem> list = module2formFiles.get(module);
        if (list == null) {
          list = new ArrayList<MyInstrumentationItem>();
          module2formFiles.put(module, list);
        }
        list.add(item);
      }
      else {
        // todo[anton] handle somehow
      }
    }
    return module2formFiles;
  }

  private static VirtualFile findFile(final VirtualFile[] outputDirectories, final String className, final Module module) {
    @NonNls final String relativepath = getClassFileName(className.replace('$', '.'), module) + ".class";
    for (final VirtualFile outputDirectory : outputDirectories) {
      final VirtualFile file = outputDirectory.findFileByRelativePath(relativepath);
      if (file != null) {
        return file;
      }
    }
    return null;
  }

  private static String getClassFileName(final String _className, final Module module) {
    final PsiClass aClass = PsiManager.getInstance(module.getProject()).findClass(_className, GlobalSearchScope.moduleScope(module));
    if (aClass == null) {
      // ?
      return _className;
    }

    PsiClass outerClass = aClass;
    while (outerClass.getParent() instanceof PsiClass) {
      outerClass = (PsiClass)outerClass.getParent();
    }

    final String outerQualifiedName = outerClass.getQualifiedName();

    assert outerQualifiedName != null;
    return outerQualifiedName.replace('.','/') + _className.substring(outerQualifiedName.length()).replace('.','$');
  }

  public FileProcessingCompiler.ProcessingItem[] process(final CompileContext context,
                                                         final FileProcessingCompiler.ProcessingItem[] items) {
    final ArrayList<FileProcessingCompiler.ProcessingItem> compiledItems = new ArrayList<FileProcessingCompiler.ProcessingItem>();

    context.getProgressIndicator().setText(UIDesignerBundle.message("progress.compiling.ui.forms"));

    final HashMap<Module, ArrayList<MyInstrumentationItem>> module2itemsList = sortByModules(items);

    int formsProcessed = 0;

    for (final Module module : module2itemsList.keySet()) {
      final String classPath =
        ProjectRootsTraversing.collectRoots(module, ProjectRootsTraversing.FULL_CLASSPATH_RECURSIVE).getPathsString();
      final ClassLoader loader = createClassLoader(classPath);

      if (GuiDesignerConfiguration.getInstance(myProject).COPY_FORMS_RUNTIME_TO_OUTPUT) {
        final String moduleOutputPath = CompilerPaths.getModuleOutputPath(module, false);
        try {
          if (moduleOutputPath != null) {
            CopyResourcesUtil.copyFormsRuntime(moduleOutputPath, false);
          }
          final String testsOutputPath = CompilerPaths.getModuleOutputPath(module, true);
          if (testsOutputPath != null && !testsOutputPath.equals(moduleOutputPath)) {
            CopyResourcesUtil.copyFormsRuntime(testsOutputPath, false);
          }
        }
        catch (IOException e) {
          addMessage(
            context,
            UIDesignerBundle.message("error.cannot.copy.gui.designer.form.runtime", module.getName(), e.toString()),
            null, CompilerMessageCategory.ERROR);
        }
      }

      final ArrayList<MyInstrumentationItem> list = module2itemsList.get(module);

      for (final MyInstrumentationItem item : list) {
        context.getProgressIndicator().setFraction((double)(++formsProcessed) / ((double)items.length));

        final VirtualFile formFile = item.getFormFile();

        final Document doc = ApplicationManager.getApplication().runReadAction(new Computable<Document>() {
          public Document compute() {
            return FileDocumentManager.getInstance().getDocument(formFile);
          }
        });
        final LwRootContainer rootContainer;
        try {
          rootContainer = Utils.getRootContainer(doc.getText(), new CompiledClassPropertiesProvider(loader));
        }
        catch (Exception e) {
          addMessage(context, UIDesignerBundle.message("error.cannot.process.form.file", e), formFile, CompilerMessageCategory.ERROR);
          continue;
        }

        final File classFile = VfsUtil.virtualToIoFile(item.getFile());
        LOG.assertTrue(classFile.exists(), classFile.getPath());

        final AsmCodeGenerator codeGenerator = new AsmCodeGenerator(rootContainer, loader,
                                                                    new PsiNestedFormLoader(module), false,
                                                                    new PsiClassWriter(module.getProject(), ClassWriter.COMPUTE_FRAMES));
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            codeGenerator.patchFile(classFile);
          }
        });
        final FormErrorInfo[] errors = codeGenerator.getErrors();
        final FormErrorInfo[] warnings = codeGenerator.getWarnings();
        for (FormErrorInfo warning : warnings) {
          addMessage(context, warning, formFile, CompilerMessageCategory.WARNING);
        }
        for (FormErrorInfo error : errors) {
          addMessage(context, error, formFile, CompilerMessageCategory.ERROR);
        }
        if (errors.length == 0) {
          compiledItems.add(item);
        }
      }
    }

    return compiledItems.toArray(new FileProcessingCompiler.ProcessingItem[compiledItems.size()]);
  }

  private void addMessage(final CompileContext context,
                          final String s,
                          final VirtualFile formFile,
                          final CompilerMessageCategory severity) {
    addMessage(context, new FormErrorInfo(null, s), formFile, severity);
  }

  private void addMessage(final CompileContext context,
                          final FormErrorInfo e,
                          final VirtualFile formFile,
                          final CompilerMessageCategory severity) {
    if (formFile != null) {
      FormElementNavigatable navigatable = new FormElementNavigatable(myProject, formFile, e.getComponentId());
      context.addMessage(severity,
                         formFile.getPresentableUrl() + ": " + e.getErrorMessage(),
                         formFile.getUrl(), -1, -1, navigatable);
    }
    else {
      context.addMessage(severity, e.getErrorMessage(), null, -1, -1);
    }
  }

  public ValidityState createValidityState(final DataInputStream is) throws IOException {
    return TimestampValidityState.load(is);
  }

  private static final class MyInstrumentationItem implements FileProcessingCompiler.ProcessingItem {
    private final VirtualFile myClassFile;
    private final VirtualFile myFormFile;
    private final TimestampValidityState myState;

    public MyInstrumentationItem(final VirtualFile classFile, final VirtualFile formFile) {
      myClassFile = classFile;
      myFormFile = formFile;
      myState = new TimestampValidityState(formFile.getTimeStamp());
    }

    @NotNull
    public VirtualFile getFile() {
      return myClassFile;
    }

    public VirtualFile getFormFile() {
      return myFormFile;
    }

    public ValidityState getValidityState() {
      return myState;
    }
  }

}
