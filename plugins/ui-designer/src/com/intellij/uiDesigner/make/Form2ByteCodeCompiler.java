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
package com.intellij.uiDesigner.make;

import com.intellij.compiler.PsiClassWriter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.GuiDesignerConfiguration;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.compiler.FormErrorInfo;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;

public final class Form2ByteCodeCompiler implements ClassInstrumentingCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.make.Form2ByteCodeCompiler");

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
  public ProcessingItem[] getProcessingItems(final CompileContext context) {
    final Project project = context.getProject();
    if (!GuiDesignerConfiguration.getInstance(project).INSTRUMENT_CLASSES) {
      return ProcessingItem.EMPTY_ARRAY;
    }

    final ArrayList<ProcessingItem> items = new ArrayList<ProcessingItem>();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final CompileScope scope = context.getCompileScope();
        final CompileScope projectScope = context.getProjectCompileScope();

        final VirtualFile[] formFiles = projectScope.getFiles(StdFileTypes.GUI_DESIGNER_FORM, true);
        if (formFiles.length==0) return;
        final CompilerManager compilerManager = CompilerManager.getInstance(project);
        final BindingsCache bindingsCache = new BindingsCache(project);

        final HashMap<Module, ArrayList<VirtualFile>> module2formFiles = sortByModules(project, formFiles);

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

              final VirtualFile classFile = findFile(context, classToBind, module);
              if (classFile == null) {
                if (scope.belongs(formFile.getUrl())) {
                  addMessage(context, UIDesignerBundle.message("error.class.to.bind.does.not.exist", classToBind), formFile,
                             CompilerMessageCategory.ERROR);
                }
                continue;
              }

              final VirtualFile alreadyProcessedForm = class2form.get(classToBind);
              if (alreadyProcessedForm != null) {
                if (belongsToCompileScope(context, formFile, classToBind)) {
                  addMessage(
                    context,
                    UIDesignerBundle.message("error.duplicate.bind",
                                             classToBind, alreadyProcessedForm.getPresentableUrl()),
                    formFile, CompilerMessageCategory.ERROR);
                }
                continue;
              }
              class2form.put(classToBind, formFile);

              final ProcessingItem item = new MyInstrumentationItem(classFile, formFile, classToBind);
              items.add(item);
            }
          }
        }
        finally {
          bindingsCache.close();
        }
      }
    });

    return items.toArray(new ProcessingItem[items.size()]);
  }

  private static boolean belongsToCompileScope(final CompileContext context, final VirtualFile formFile, final String classToBind) {
    final CompileScope compileScope = context.getCompileScope();
    if (compileScope.belongs(formFile.getUrl())) {
      return true;
    }
    final VirtualFile sourceFile = findSourceFile(context, formFile, classToBind);
    return sourceFile != null && compileScope.belongs(sourceFile.getUrl());
  }

  private static HashMap<Module, ArrayList<VirtualFile>> sortByModules(final Project project, final VirtualFile[] formFiles) {
    final HashMap<Module, ArrayList<VirtualFile>> module2formFiles = new HashMap<Module,ArrayList<VirtualFile>>();
    for (final VirtualFile formFile : formFiles) {
      final Module module = ModuleUtil.findModuleForFile(formFile, project);
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

  private static HashMap<Module, ArrayList<MyInstrumentationItem>> sortByModules(final Project project, final ProcessingItem[] items) {
    final HashMap<Module, ArrayList<MyInstrumentationItem>> module2formFiles = new HashMap<Module,ArrayList<MyInstrumentationItem>>();
    for (ProcessingItem item1 : items) {
      final MyInstrumentationItem item = (MyInstrumentationItem)item1;
      final VirtualFile formFile = item.getFormFile();

      final Module module = ModuleUtil.findModuleForFile(formFile, project);
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

  @Nullable
  private static VirtualFile findFile(final CompileContext context, final String className, final Module module) {
    /*for most cases (top-level classes) this will work*/
    VirtualFile file = findFileByRelativePath(context, module, className.replace('.', '/') + ".class"); 
    if (file == null) {
      // getClassFileName() is much longer than simply conversion from dots into slashes, but works for inner classes
      file = findFileByRelativePath(context, module, getClassFileName(className.replace('$', '.'), module) + ".class");
    }
    return file;
  }

  private static VirtualFile findFileByRelativePath(final CompileContext context, final Module module, final String relativepath) {
    final VirtualFile output = context.getModuleOutputDirectory(module);
    VirtualFile file = output != null? output.findFileByRelativePath(relativepath) : null;
    if (file == null) {
      final VirtualFile testsOutput = context.getModuleOutputDirectoryForTests(module);
      if (testsOutput != null && !testsOutput.equals(output)) {
        file = testsOutput.findFileByRelativePath(relativepath);
      }
    }
    return file;
  }

  private static String getClassFileName(final String _className, final Module module) {
    final PsiClass aClass = JavaPsiFacade.getInstance(module.getProject()).findClass(_className, GlobalSearchScope.moduleScope(module));
    if (aClass == null) {
      return _className.replace('.', '/');
    }

    PsiClass outerClass = aClass;
    while (outerClass.getParent() instanceof PsiClass) {
      outerClass = (PsiClass)outerClass.getParent();
    }

    final String outerQualifiedName = outerClass.getQualifiedName();

    assert outerQualifiedName != null;
    return outerQualifiedName.replace('.','/') + _className.substring(outerQualifiedName.length()).replace('.','$');
  }

  public ProcessingItem[] process(final CompileContext context, final ProcessingItem[] items) {
    final ArrayList<ProcessingItem> compiledItems = new ArrayList<ProcessingItem>();

    context.getProgressIndicator().pushState();
    context.getProgressIndicator().setText(UIDesignerBundle.message("progress.compiling.ui.forms"));

    final Project project = context.getProject();
    final HashMap<Module, ArrayList<MyInstrumentationItem>> module2itemsList = sortByModules(project, items);

    int formsProcessed = 0;

    for (final Module module : module2itemsList.keySet()) {
      final String classPath = OrderEnumerator.orderEntries(module).recursively().getPathsList().getPathsString();
      final ClassLoader loader = createClassLoader(classPath);

      if (GuiDesignerConfiguration.getInstance(project).COPY_FORMS_RUNTIME_TO_OUTPUT) {
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
        //context.getProgressIndicator().setFraction((double)++formsProcessed / (double)items.length);
        
        final VirtualFile formFile = item.getFormFile();
        context.getProgressIndicator().setText2(formFile.getPresentableUrl());

        final Document doc = ApplicationManager.getApplication().runReadAction(new Computable<Document>() {
          public Document compute() {
            if (!belongsToCompileScope(context, formFile, item.getClassToBindFQname())) {
              return null;
            }
            return FileDocumentManager.getInstance().getDocument(formFile);
          }
        });
        if (doc == null) {
          continue; // does not belong to current scope
        }
        
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
                                                                    new PsiClassWriter(module));
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
    context.getProgressIndicator().popState();

    return compiledItems.toArray(new ProcessingItem[compiledItems.size()]);
  }

  private static void addMessage(final CompileContext context,
                          final String s,
                          final VirtualFile formFile,
                          final CompilerMessageCategory severity) {
    addMessage(context, new FormErrorInfo(null, s), formFile, severity);
  }

  private static void addMessage(final CompileContext context,
                          final FormErrorInfo e,
                          final VirtualFile formFile,
                          final CompilerMessageCategory severity) {
    if (formFile != null) {
      FormElementNavigatable navigatable = new FormElementNavigatable(context.getProject(), formFile, e.getComponentId());
      context.addMessage(severity,
                         formFile.getPresentableUrl() + ": " + e.getErrorMessage(),
                         formFile.getUrl(), -1, -1, navigatable);
    }
    else {
      context.addMessage(severity, e.getErrorMessage(), null, -1, -1);
    }
  }

  public ValidityState createValidityState(final DataInput in) throws IOException {
    return TimestampValidityState.load(in);
  }

  public static VirtualFile findSourceFile(final CompileContext context, final VirtualFile formFile, final String className) {
    final Module module = context.getModuleByFile(formFile);
    if (module == null) {
      return null;
    }
    final PsiClass aClass = FormEditingUtil.findClassToBind(module, className);
    if (aClass == null) {
      return null;
    }

    final PsiFile containingFile = aClass.getContainingFile();
    if (containingFile == null){
      return null;
    }

    return containingFile.getVirtualFile();
  }

  private static final class MyInstrumentationItem implements ProcessingItem {
    private final VirtualFile myClassFile;
    private final VirtualFile myFormFile;
    private final String myClassToBindFQname;
    private final TimestampValidityState myState;

    private MyInstrumentationItem(final VirtualFile classFile, final VirtualFile formFile, final String classToBindFQname) {
      myClassFile = classFile;
      myFormFile = formFile;
      myClassToBindFQname = classToBindFQname;
      myState = new TimestampValidityState(formFile.getTimeStamp());
    }

    @NotNull
    public VirtualFile getFile() {
      return myClassFile;
    }

    public VirtualFile getFormFile() {
      return myFormFile;
    }

    public String getClassToBindFQname() {
      return myClassToBindFQname;
    }

    public ValidityState getValidityState() {
      return myState;
    }
  }

}
