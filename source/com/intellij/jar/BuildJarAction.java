package com.intellij.jar;

import com.intellij.j2ee.make.*;
import com.intellij.j2ee.module.LibraryLink;
import com.intellij.j2ee.module.ModuleContainer;
import com.intellij.j2ee.module.ModuleLink;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.DummyCompileContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.wm.WindowManager;
import gnu.trove.THashSet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.jar.JarOutputStream;

/**
 * @author cdr
 */
public class BuildJarAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.jar.BuildJarAction");

  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    BuildJarActionDialog dialog = new BuildJarActionDialog(project);
    dialog.show();
    if (dialog.isOK()) {
      buildJars(project);
    }
  }

  private static void buildJars(final Project project) {
    ApplicationManager.getApplication().runProcessWithProgressSynchronously(new Runnable(){
      public void run() {
        Module[] modules = ModuleManager.getInstance(project).getModules();
        try {
          for (Module module : modules) {
            BuildJarSettings buildJarSettings = BuildJarSettings.getInstance(module);
            if (buildJarSettings == null || !buildJarSettings.isBuildJar()) continue;
            String presentableJarPath = "'"+buildJarSettings.getJarPath()+"'";
            ProgressManager.getInstance().getProgressIndicator().setText("Building jar "+presentableJarPath+" ...");
            buildJar(module, buildJarSettings);
            WindowManager.getInstance().getStatusBar(project).setInfo("Jar has been built in "+presentableJarPath);
          }
        }
        catch (ProcessCanceledException e) {
          WindowManager.getInstance().getStatusBar(project).setInfo("Jar creation has been canceled");
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    },"Building Jar", true, project);
  }

  private static void buildJar(final Module module, final BuildJarSettings buildJarSettings) throws IOException {
    ModuleContainer moduleContainer = buildJarSettings.getModuleContainer();
    String jarPath = buildJarSettings.getJarPath();
    final File jarFile = new File(jarPath);
    jarFile.delete();

    FileUtil.createParentDirs(jarFile);

    // write temp file and rename it to the jar to avoid deployment of incomplete jar. SCR #30303
    final File tempFile = File.createTempFile("___"+ FileUtil.getNameWithoutExtension(jarFile), ".jar", jarFile.getParentFile());

    final JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(tempFile));

    BuildRecipeImpl buildRecipe = new BuildRecipeImpl();
    LibraryLink[] libraries = moduleContainer.getContainingLibraries();
    final DummyCompileContext compileContext = DummyCompileContext.getInstance();
    for (LibraryLink libraryLink : libraries) {
      MakeUtil.getInstance().addLibraryLink(compileContext, buildRecipe, libraryLink, module, null);
    }
    ModuleLink[] modules = moduleContainer.getContainingModules();
    MakeUtil.getInstance().addJavaModuleOutputs(module, modules, buildRecipe, compileContext, null);

    final Set<String> tempWrittenRelativePaths = new THashSet<String>();
    final BuildRecipeImpl dependencies = new BuildRecipeImpl();
    try {
      buildRecipe.visitInstructionsWithExceptions(new BuildInstructionVisitor() {
        public boolean visitInstruction(BuildInstruction instruction) throws IOException {
          ProgressManager.getInstance().checkCanceled();
          instruction.addFilesToJar(compileContext, tempFile, jarOutputStream, dependencies, tempWrittenRelativePaths, null);
          if (instruction instanceof FileCopyInstruction) {
            FileCopyInstruction fileCopyInstruction = (FileCopyInstruction)instruction;
            File file = fileCopyInstruction.getFile();
            String presentablePath = file == null ? "" : FileUtil.toSystemDependentName(file.getPath());
            ProgressManager.getInstance().getProgressIndicator().setText2("Processing file "+presentablePath+" ...");
          }
          return true;
        }
      }, false);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.error(e);
    }
    finally {
      jarOutputStream.close();
      try {
        FileUtil.rename(tempFile, jarFile);
      }
      catch (IOException e) {
        //context.addMessage(CompilerMessageCategory.ERROR, "Cannot overwrite file '"+FileUtil.toSystemDependentName(jarFile.getPath())+"'." +
        //                   " Copy have been saved to '" + FileUtil.toSystemDependentName(tempFile.getPath())+"'.",null,-1,-1);
        LOG.error(e);
      }
    }
  }

  public void update(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    e.getPresentation().setEnabled(project != null);
  }
}
