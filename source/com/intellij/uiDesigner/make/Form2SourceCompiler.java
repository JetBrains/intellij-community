package com.intellij.uiDesigner.make;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.uiDesigner.GuiDesignerConfiguration;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.compiler.AlienFormFileException;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.text.MessageFormat;

public final class Form2SourceCompiler implements SourceInstrumentingCompiler{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.make.Form2SourceCompiler");

  private final Project myProject;

  public Form2SourceCompiler(final Project project) {
    myProject = project;
  }

  public String getDescription() {
    return UIDesignerBundle.message("component.gui.designer.form.to.source.compiler");
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  public ProcessingItem[] getProcessingItems(final CompileContext context) {
    if (GuiDesignerConfiguration.getInstance(myProject).INSTRUMENT_CLASSES) {
      return ProcessingItem.EMPTY_ARRAY;
    }

    final ArrayList<ProcessingItem> items = new ArrayList<ProcessingItem>();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final CompileScope scope = context.getCompileScope();
        final CompileScope projectScope = context.getProjectCompileScope();

        final VirtualFile[] formFiles = projectScope.getFiles(StdFileTypes.GUI_DESIGNER_FORM, true);
        final CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(myProject);
        final BindingsCache bindingsCache = new BindingsCache(myProject);

        try {
          final HashMap<String, VirtualFile> class2form = new HashMap<String, VirtualFile>();

          for (int i = 0; i < formFiles.length; i++) {
            final VirtualFile formFile = formFiles[i];

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
              addError(context, UIDesignerBundle.message("error.cannot.process.form.file", e), formFile);
              continue;
            }

            if (classToBind == null) {
              continue;
            }

            final VirtualFile sourceFile = FormCompilerManager.findSourceFile(context, formFile, classToBind);
            if (sourceFile == null) {
              if (scope.belongs(formFile.getUrl())) {
                addError(context, UIDesignerBundle.message("error.class.to.bind.does.not.exist", classToBind), formFile);
              }
              continue;
            }

            final boolean inScope = scope.belongs(sourceFile.getUrl()) || scope.belongs(formFile.getUrl());

            final VirtualFile alreadyProcessedForm = class2form.get(classToBind);
            if (alreadyProcessedForm != null) {
              if (inScope) {
                addError(
                  context,
                  UIDesignerBundle.message("error.duplicate.bind",
                                           classToBind, alreadyProcessedForm.getPresentableUrl()),
                  formFile
                );
              }
              continue;
            }
            class2form.put(classToBind, formFile);

            if (!inScope) {
              continue;
            }

            final ProcessingItem item = new MyInstrumentationItem(sourceFile, formFile);
            items.add(item);
          }
        }
        finally {
          bindingsCache.save();
        }
      }
    });

    return items.toArray(new ProcessingItem[items.size()]);
  }

  public ProcessingItem[] process(final CompileContext context, final ProcessingItem[] items) {
    final ArrayList<ProcessingItem> compiledItems = new ArrayList<ProcessingItem>();

    context.getProgressIndicator().setText(UIDesignerBundle.message("progress.compiling.ui.forms"));

    int formsProcessed = 0;

    final FormSourceCodeGenerator generator = new FormSourceCodeGenerator(myProject);

    final HashSet<Module> processedModules = new HashSet<Module>();

    for (int i = 0; i < items.length; i++) {
      context.getProgressIndicator().setFraction((double)(++formsProcessed) / ((double)items.length));

      final MyInstrumentationItem item = (MyInstrumentationItem)items[i];

      final VirtualFile formFile = item.getFormFile();

      if (GuiDesignerConfiguration.getInstance(myProject).COPY_FORMS_RUNTIME_TO_OUTPUT) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            final Module module = ModuleUtil.getModuleForFile(myProject, formFile);
            if (module != null && !processedModules.contains(module)) {
              processedModules.add(module);
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
                  UIDesignerBundle.message("error.cannot.copy.gui.designer.form.runtime",
                                           module.getName(), e.toString()),
                  null
                );
              }
            }
          }
        });
      }

      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
          public void run() {
            CommandProcessor.getInstance().executeCommand(
              myProject,
              new Runnable() {
                public void run() {
                  ApplicationManager.getApplication().runWriteAction(
                    new Runnable() {
                      public void run() {
                        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
                        generator.generate(formFile);
                        final ArrayList<String> errors = generator.getErrors();
                        if (errors.size() == 0) {
                          compiledItems.add(item);
                        }
                        else {
                          for (int j = 0; j < errors.size(); j++) {
                            final String s = errors.get(j);
                            addError(context, s, formFile);
                          }
                        }
                        FileDocumentManager.getInstance().saveAllDocuments();
                      }
                    }
                  );
                }
              },
              "",
              null
            );
          }
        }, ModalityState.NON_MMODAL);
    }
    return compiledItems.toArray(new ProcessingItem[compiledItems.size()]);
  }

  private static void addError(final CompileContext context, final String message, final VirtualFile formFile) {
    if (formFile != null) {
      context.addMessage(CompilerMessageCategory.ERROR, formFile.getPresentableUrl() + ": " + message, formFile.getUrl(), -1, -1);
    }
    else {
      context.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
    }
  }

  public ValidityState createValidityState(final DataInputStream is) throws IOException {
    return TimestampValidityState.load(is);
  }

  private static final class MyInstrumentationItem implements ProcessingItem {
    private final VirtualFile mySourceFile;
    private final VirtualFile myFormFile;
    private final TimestampValidityState myState;

    public MyInstrumentationItem(final VirtualFile sourceFile, final VirtualFile formFile) {
      mySourceFile = sourceFile;
      myFormFile = formFile;
      myState = new TimestampValidityState(formFile.getTimeStamp());
    }

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
