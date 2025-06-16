// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.make;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.GuiDesignerConfiguration;
import com.intellij.uiDesigner.GuiFormFileType;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.compiler.FormErrorInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.java.CopyResourcesUtil;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.util.*;

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

  @Override
  public @NotNull String getDescription() {
    return UIDesignerBundle.message("component.gui.designer.form.to.source.compiler");
  }

  @Override
  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  @Override
  public ProcessingItem @NotNull [] getProcessingItems(final @NotNull CompileContext context) {
    final Project project = context.getProject();
    GuiDesignerConfiguration designerConfiguration = GuiDesignerConfiguration.getInstance(project);

    if (designerConfiguration.INSTRUMENT_CLASSES || designerConfiguration.GENERATE_SOURCES_ON_SAVE) {
      return ProcessingItem.EMPTY_ARRAY;
    }

    final ArrayList<ProcessingItem> items = new ArrayList<>();
    DumbService.getInstance(project).runReadActionInSmartMode(() -> {
      final CompileScope scope = context.getCompileScope();
      final CompileScope projectScope = context.getProjectCompileScope();

      final VirtualFile[] formFiles = projectScope.getFiles(GuiFormFileType.INSTANCE, true);
      final CompilerManager compilerManager = CompilerManager.getInstance(project);
      final BindingsCache bindingsCache = new BindingsCache(project);

      ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(context.getProject());

      try {
        final HashMap<String, VirtualFile> class2form = new HashMap<>();

        for (final VirtualFile formFile : formFiles) {
          if (compilerManager.isExcludedFromCompilation(formFile)) {
            continue;
          }

          if (!fileIndex.isUnderSourceRootOfType(formFile, Set.of(JavaSourceRootType.SOURCE, JavaSourceRootType.TEST_SOURCE))) {
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

    return items.toArray(ProcessingItem.EMPTY_ARRAY);
  }

  @Override
  public ProcessingItem[] process(final @NotNull CompileContext context, final ProcessingItem @NotNull [] items) {
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
          if (errors.isEmpty()) {
            compiledItems.add(item);
          }
          else {
            for (final FormErrorInfo e : errors) {
              addError(context, e, formFile);
            }
          }
        }), "", null);
        FileDocumentManager.getInstance().saveAllDocuments();
      }, ModalityState.nonModal());
    }

    if (!filesToRefresh.isEmpty()) {
      LocalFileSystem.getInstance().refreshIoFiles(filesToRefresh);
    }

    return compiledItems.toArray(ProcessingItem.EMPTY_ARRAY);
  }

  private static void addError(final CompileContext context, final FormErrorInfo e, final VirtualFile formFile) {
    @NlsSafe String message = e.getErrorMessage();
    if (formFile != null) {
      FormElementNavigatable navigatable = new FormElementNavigatable(context.getProject(), formFile, e.getComponentId());
      context.addMessage(CompilerMessageCategory.ERROR,
                         formFile.getPresentableUrl() + ": " + message,
                         formFile.getUrl(), -1, -1, navigatable);
    }
    else {
      context.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
    }
  }

  @Override
  public ValidityState createValidityState(final DataInput in) throws IOException {
    return TimestampValidityState.load(in);
  }

  private static final class MyInstrumentationItem implements ProcessingItem {
    private final @NotNull VirtualFile mySourceFile;
    private final VirtualFile myFormFile;
    private final TimestampValidityState myState;

    MyInstrumentationItem(final @NotNull VirtualFile sourceFile, final VirtualFile formFile) {
      mySourceFile = sourceFile;
      myFormFile = formFile;
      myState = new TimestampValidityState(formFile.getTimeStamp());
    }

    @Override
    public @NotNull VirtualFile getFile() {
      return mySourceFile;
    }

    public VirtualFile getFormFile() {
      return myFormFile;
    }

    @Override
    public ValidityState getValidityState() {
      return myState;
    }
  }

}
