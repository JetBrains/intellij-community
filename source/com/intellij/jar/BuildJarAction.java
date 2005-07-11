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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.wm.WindowManager;
import gnu.trove.THashSet;

import java.io.*;
import java.util.Collection;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * @author cdr
 */
public class BuildJarAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.jar.BuildJarAction");

  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    Collection<Module> modulesToJar = BuildJarActionDialog.getModulesToJar(project);
    if (modulesToJar.size() == 0) {
      Messages.showErrorDialog(project, "There are no Java modules found in the project.\nOnly Java modules can be jarred.", "No Modules To Jar Found");
      return;
    }
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
          Messages.showErrorDialog(project, e.toString(), "Error Creating Jar");
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
    final FileFilter allFilesFilter = new FileFilter() {
      public boolean accept(File f) {
        return true;
      }

      public String getDescription() {
        return "All file types";
      }
    };
    BuildRecipeImpl buildRecipe = new BuildRecipeImpl();
    LibraryLink[] libraries = moduleContainer.getContainingLibraries();
    final DummyCompileContext compileContext = DummyCompileContext.getInstance();
    for (LibraryLink libraryLink : libraries) {
      MakeUtil.getInstance().addLibraryLink(compileContext, buildRecipe, libraryLink, module, null);
    }
    ModuleLink[] modules = moduleContainer.getContainingModules();
    MakeUtil.getInstance().addJavaModuleOutputs(module, modules, buildRecipe, compileContext, null);
    Manifest manifest = MakeUtil.getInstance().createManifest(buildRecipe);
    String mainClass = buildJarSettings.getMainClass();
    if (manifest != null && !Comparing.strEqual(mainClass, null)) {
      manifest.getMainAttributes().putValue("Main-Class",mainClass);
    }

    // write temp file and rename it to the jar to avoid deployment of incomplete jar. SCR #30303
    final File tempFile = File.createTempFile("_"+ FileUtil.getNameWithoutExtension(jarFile), ".jar", jarFile.getParentFile());
    final JarOutputStream jarOutputStream = manifest == null ?
                                            new JarOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile))) :
                                            new JarOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile)), manifest);

    final Set<String> tempWrittenRelativePaths = new THashSet<String>();
    final BuildRecipeImpl dependencies = new BuildRecipeImpl();
    try {
      buildRecipe.visitInstructionsWithExceptions(new BuildInstructionVisitor() {
        public boolean visitInstruction(BuildInstruction instruction) throws IOException {
          ProgressManager.getInstance().checkCanceled();
          if (instruction instanceof FileCopyInstruction) {
            FileCopyInstruction fileCopyInstruction = (FileCopyInstruction)instruction;
            File file = fileCopyInstruction.getFile();
            if (file == null || !file.exists()) return true;
            String presentablePath = FileUtil.toSystemDependentName(file.getPath());
            ProgressManager.getInstance().getProgressIndicator().setText2("Processing file "+presentablePath+" ...");
          }
          instruction.addFilesToJar(compileContext, tempFile, jarOutputStream, dependencies, tempWrittenRelativePaths, allFilesFilter);
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
        String message = "Cannot overwrite file '" + FileUtil.toSystemDependentName(jarFile.getPath()) + "'." +
                         " Copy have been saved to '" + FileUtil.toSystemDependentName(tempFile.getPath()) + "'.";
        Messages.showErrorDialog(module.getProject(), message, "Error Creating Jar");
      }
    }
  }

  public void update(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    e.getPresentation().setEnabled(project != null);
  }
}
