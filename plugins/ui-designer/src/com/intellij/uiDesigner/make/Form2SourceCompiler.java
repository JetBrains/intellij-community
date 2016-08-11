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

import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.GuiDesignerConfiguration;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.compiler.FormErrorInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.java.CopyResourcesUtil;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public final class Form2SourceCompiler implements SourceInstrumentingCompiler{

  private static VirtualFile findSourceFile(final CompileContext context, final VirtualFile formFile, final String className) {
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

  @NotNull
  public String getDescription() {
    return UIDesignerBundle.message("component.gui.designer.form.to.source.compiler");
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  @NotNull
  public ProcessingItem[] getProcessingItems(final CompileContext context) {
    final Project project = context.getProject();
    if (GuiDesignerConfiguration.getInstance(project).INSTRUMENT_CLASSES) {
      return ProcessingItem.EMPTY_ARRAY;
    }

    final ArrayList<ProcessingItem> items = new ArrayList<>();
    DumbService.getInstance(project).runReadActionInSmartMode(() -> {
      final CompileScope scope = context.getCompileScope();
      final CompileScope projectScope = context.getProjectCompileScope();

      final VirtualFile[] formFiles = projectScope.getFiles(StdFileTypes.GUI_DESIGNER_FORM, true);
      final CompilerManager compilerManager = CompilerManager.getInstance(project);
      final BindingsCache bindingsCache = new BindingsCache(project);

      try {
        final HashMap<String, VirtualFile> class2form = new HashMap<>();

        for (final VirtualFile formFile : formFiles) {
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
            addError(context, new FormErrorInfo(null, UIDesignerBundle.message("error.cannot.process.form.file", e)), formFile);
            continue;
          }

          if (classToBind == null) {
            continue;
          }

          final VirtualFile sourceFile = findSourceFile(context, formFile, classToBind);
          if (sourceFile == null) {
            if (scope.belongs(formFile.getUrl())) {
              addError(context, new FormErrorInfo(null, UIDesignerBundle.message("error.class.to.bind.does.not.exist", classToBind)), formFile);
            }
            continue;
          }

          final boolean inScope = scope.belongs(sourceFile.getUrl()) || scope.belongs(formFile.getUrl());

          final VirtualFile alreadyProcessedForm = class2form.get(classToBind);
          if (alreadyProcessedForm != null) {
            if (inScope) {
              addError(context, new FormErrorInfo(null, UIDesignerBundle.message("error.duplicate.bind", classToBind, alreadyProcessedForm.getPresentableUrl())), formFile);
            }
            continue;
          }
          class2form.put(classToBind, formFile);

          if (!inScope) {
            continue;
          }

          items.add(new MyInstrumentationItem(sourceFile, formFile));
        }
      }
      finally {
        bindingsCache.close();
      }
    });

    return items.toArray(new ProcessingItem[items.size()]);
  }

  public ProcessingItem[] process(final CompileContext context, final ProcessingItem[] items) {
    final ArrayList<ProcessingItem> compiledItems = new ArrayList<>();

    context.getProgressIndicator().setText(UIDesignerBundle.message("progress.compiling.ui.forms"));

    int formsProcessed = 0;

    final Project project = context.getProject();
    final FormSourceCodeGenerator generator = new FormSourceCodeGenerator(project);

    final HashSet<Module> processedModules = new HashSet<>();

    final List<File> filesToRefresh = new ArrayList<>();
    for (ProcessingItem item1 : items) {
      context.getProgressIndicator().setFraction((double)(++formsProcessed) / ((double)items.length));

      final MyInstrumentationItem item = (MyInstrumentationItem)item1;

      final VirtualFile formFile = item.getFormFile();

      if (GuiDesignerConfiguration.getInstance(project).COPY_FORMS_RUNTIME_TO_OUTPUT) {
        ApplicationManager.getApplication().runReadAction(() -> {
          final Module module = ModuleUtilCore.findModuleForFile(formFile, project);
          if (module != null && !processedModules.contains(module)) {
            processedModules.add(module);
            final String moduleOutputPath = CompilerPaths.getModuleOutputPath(module, false);
            try {
              if (moduleOutputPath != null) {
                filesToRefresh.addAll(CopyResourcesUtil.copyFormsRuntime(moduleOutputPath, false));
              }
              final String testsOutputPath = CompilerPaths.getModuleOutputPath(module, true);
              if (testsOutputPath != null && !testsOutputPath.equals(moduleOutputPath)) {
                filesToRefresh.addAll(CopyResourcesUtil.copyFormsRuntime(testsOutputPath, false));
              }
            }
            catch (IOException e) {
              addError(
                context,
                new FormErrorInfo(null, UIDesignerBundle.message("error.cannot.copy.gui.designer.form.runtime",
                                         module.getName(), e.toString())),
                null
              );
            }
          }
        });
      }

      ApplicationManager.getApplication().invokeAndWait(() -> {
        CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
          PsiDocumentManager.getInstance(project).commitAllDocuments();
          generator.generate(formFile);
          final ArrayList<FormErrorInfo> errors = generator.getErrors();
          if (errors.size() == 0) {
            compiledItems.add(item);
          }
          else {
            for (final FormErrorInfo e : errors) {
              addError(context, e, formFile);
            }
          }
        }), "", null);
        FileDocumentManager.getInstance().saveAllDocuments();
      }, ModalityState.NON_MODAL);
    }

    CompilerUtil.refreshIOFiles(filesToRefresh);
    return compiledItems.toArray(new ProcessingItem[compiledItems.size()]);
  }

  private static void addError(final CompileContext context, final FormErrorInfo e, final VirtualFile formFile) {
    if (formFile != null) {
      FormElementNavigatable navigatable = new FormElementNavigatable(context.getProject(), formFile, e.getComponentId());
      context.addMessage(CompilerMessageCategory.ERROR,
                         formFile.getPresentableUrl() + ": " + e.getErrorMessage(), 
                         formFile.getUrl(), -1, -1, navigatable);
    }
    else {
      context.addMessage(CompilerMessageCategory.ERROR, e.getErrorMessage(), null, -1, -1);
    }
  }

  public ValidityState createValidityState(final DataInput in) throws IOException {
    return TimestampValidityState.load(in);
  }

  private static final class MyInstrumentationItem implements ProcessingItem {
    @NotNull private final VirtualFile mySourceFile;
    private final VirtualFile myFormFile;
    private final TimestampValidityState myState;

    public MyInstrumentationItem(@NotNull final VirtualFile sourceFile, final VirtualFile formFile) {
      mySourceFile = sourceFile;
      myFormFile = formFile;
      myState = new TimestampValidityState(formFile.getTimeStamp());
    }

    @NotNull
    public VirtualFile getFile() {
      return mySourceFile;
    }

    public VirtualFile getFormFile() {
      return myFormFile;
    }

    public ValidityState getValidityState() {
      return myState;
    }
  }

}
