package com.intellij.uiDesigner.make;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompilerPathsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootsTraversing;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.uiDesigner.GuiDesignerConfiguration;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.compiler.CodeGenerator;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.util.BcelUtils;
import org.apache.bcel.util.ClassPath;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;

public final class Form2ByteCodeCompiler implements ClassInstrumentingCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.make.Form2ByteCodeCompiler");

  private final Project myProject;

  public Form2ByteCodeCompiler(final Project project) {
    myProject = project;
  }

  public String getDescription() {
    return "GUI Designer form to bytecode compiler";
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  /**
   * @return never <code>null</code>
   */
  public static URLClassLoader createClassLoader(final String classPath){
    if (classPath == null) {
      throw new IllegalArgumentException("classPath cannot be null");
    }
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

  public FileProcessingCompiler.ProcessingItem[] getProcessingItems(final CompileContext context) {
    if (!GuiDesignerConfiguration.getInstance(myProject).INSTRUMENT_CLASSES) {
      return ProcessingItem.EMPTY_ARRAY;
    }

    final ArrayList<FileProcessingCompiler.ProcessingItem> items = new ArrayList<FileProcessingCompiler.ProcessingItem>();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final CompileScope scope = context.getCompileScope();

        final VirtualFile[] formFiles = scope.getFiles(StdFileTypes.GUI_DESIGNER_FORM, true);
        final CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(myProject);
        final BindingsCache bindingsCache = new BindingsCache(myProject);
        final VirtualFile[] outputDirectories = CompilerPathsEx.getOutputDirectories(
          ModuleManager.getInstance(myProject).getSortedModules()
        );

        final HashMap<Module, ArrayList<VirtualFile>> module2formFiles = sortByModules(formFiles);

        try {
          for (Iterator<Module> modules = module2formFiles.keySet().iterator(); modules.hasNext();) {
            final Module module = modules.next();

            final HashMap<String, VirtualFile> class2form = new HashMap<String, VirtualFile>();

            final ArrayList list = module2formFiles.get(module);
            for (int i = 0; i < list.size(); i++) {
              final VirtualFile formFile = (VirtualFile)list.get(i);

              if (compilerConfiguration.isExcludedFromCompilation(formFile)) {
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
                addError(context, "Cannot process form file. Reason: " + e, formFile);
                continue;
              }

              if (classToBind == null) {
                continue;
              }

              final VirtualFile classFile = findFile(outputDirectories, classToBind, module);
              if (classFile == null) {
                if (context.getCompileScope().belongs(formFile.getUrl())) {
                  addError(context, "Class to bind does not exist: " + classToBind, formFile);
                }
                continue;
              }

              final VirtualFile alreadyProcessedForm = class2form.get(classToBind);
              if (alreadyProcessedForm != null) {
                addError(
                  context,
                  "The form is bound to the class " + classToBind + ".\n" +
                  "Another form " + alreadyProcessedForm.getPresentableUrl() + " is also bound to this class.",
                  formFile
                );
                continue;
              }
              class2form.put(classToBind, formFile);

              final FileProcessingCompiler.ProcessingItem item = new MyInstrumentationItem(classFile, formFile);
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
    for (int i = 0; i < formFiles.length; i++) {
      final VirtualFile formFile = formFiles[i];
          
      final Module module = ModuleUtil.getModuleForFile(myProject, formFile);
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
    for (int i = 0; i < items.length; i++) {
      final MyInstrumentationItem item = (MyInstrumentationItem)items[i];
      final VirtualFile formFile = item.getFormFile();
          
      final Module module = ModuleUtil.getModuleForFile(myProject, formFile);
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
    final String relativepath = getClassFileName(className.replace('$', '.'), module) + ".class";
    for (int idx = 0; idx < outputDirectories.length; idx++) {
      final VirtualFile outputDirectory = outputDirectories[idx];
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

    return outerQualifiedName.replace('.','/') + _className.substring(outerQualifiedName.length()).replace('.','$');
  }

  public FileProcessingCompiler.ProcessingItem[] process(final CompileContext context,
                                                                final FileProcessingCompiler.ProcessingItem[] items) {
    final ArrayList<FileProcessingCompiler.ProcessingItem> compiledItems = new ArrayList<FileProcessingCompiler.ProcessingItem>();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        context.getProgressIndicator().setText("Compiling UI forms...");

        final HashMap<Module, ArrayList<MyInstrumentationItem>> module2itemsList = sortByModules(items);

        int formsProcessed = 0;

        for (Iterator<Module> iterator = module2itemsList.keySet().iterator(); iterator.hasNext();) {
          final Module module = iterator.next();
          final String classPath = ProjectRootsTraversing.collectRoots(module, ProjectRootsTraversing.FULL_CLASSPATH_RECURSIVE).getPathsString();
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
              addError(
                context,
                "Cannot copy GUI designer form runtime classes to the output directory of module " + module.getName() +
                ".\nReason: " + e.toString(),
                null
              );
            }
          }

          BcelUtils.initBcel(new ClassPath(classPath));
          try {
            final ArrayList<MyInstrumentationItem> list = module2itemsList.get(module);
          
            for (int i = 0; i < list.size(); i++) {
              context.getProgressIndicator().setFraction((double)(++formsProcessed) / ((double)items.length));
              
              final MyInstrumentationItem item = list.get(i);
            
              final VirtualFile formFile = item.getFormFile();

              final Document doc = FileDocumentManager.getInstance().getDocument(formFile);
              final LwRootContainer rootContainer;
              try {
                rootContainer = Utils.getRootContainer(doc.getText(), new CompiledClassPropertiesProvider(loader));
              }
              catch (Exception e) {
                addError(context, "Cannot process form file. Reason: " + e, formFile);
                continue;
              }

              final File classFile = VfsUtil.virtualToIoFile(item.getFile());
              LOG.assertTrue(classFile.exists());

              final CodeGenerator codeGenerator = new CodeGenerator(rootContainer, classFile, loader);
              codeGenerator.patch();
              final String[] errors = codeGenerator.getErrors();
              final String[] warnings = codeGenerator.getWarnings();
              for (int j = 0; j < warnings.length; j++) {
                addWarning(context, warnings[j], formFile);
              }
              for (int j = 0; j < errors.length; j++) {
                addError(context, errors[j], formFile);
              }
              if (errors.length == 0) {
                compiledItems.add(item);
              }
            }
          }
          finally {
            BcelUtils.disposeBcel();
          }
        }
      }
    });

    return compiledItems.toArray(new FileProcessingCompiler.ProcessingItem[compiledItems.size()]);
  }

  private static void addError(final CompileContext context, final String message, final VirtualFile formFile) {
    if (formFile != null) {
      context.addMessage(CompilerMessageCategory.ERROR, formFile.getPresentableUrl() + ": " + message,
                         formFile.getUrl(), -1, -1);
    }
    else {
      context.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
    }
  }

  private static void addWarning(final CompileContext context, final String message, final VirtualFile formFile) {
    if (formFile != null) {
      context.addMessage(CompilerMessageCategory.WARNING, formFile.getPresentableUrl() + ": " + message,
                         formFile.getUrl(), -1, -1);
    }
    else {
      context.addMessage(CompilerMessageCategory.WARNING, message, null, -1, -1);
    }
  }

  public ValidityState createValidityState(final DataInputStream is) throws IOException {
    return new TimestampValidityState(is.readLong());
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
