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
import com.intellij.ide.IdeBundle;
import gnu.trove.THashSet;

import java.io.*;
import java.util.Collection;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.jetbrains.annotations.NonNls;

/**
 * @author cdr
 */
public class BuildJarAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.jar.BuildJarAction");
  @NonNls private static final String MAIN_CLASS = "Main-Class";
  @NonNls private static final String JAR_EXTENSION = ".jar";

  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    Collection<Module> modulesToJar = BuildJarActionDialog.getModulesToJar(project);
    if (modulesToJar.size() == 0) {
      Messages.showErrorDialog(project, IdeBundle.message("jar.no.java.modules.in.project.error"),
                               IdeBundle.message("jar.no.java.modules.in.project.title"));
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
            ProgressManager.getInstance().getProgressIndicator().setText(IdeBundle.message("jar.build.progress", presentableJarPath));
            buildJar(module, buildJarSettings);
            WindowManager.getInstance().getStatusBar(project).setInfo(IdeBundle.message("jar.build.success.message", presentableJarPath));
          }
        }
        catch (ProcessCanceledException e) {
          WindowManager.getInstance().getStatusBar(project).setInfo(IdeBundle.message("jar.build.cancelled"));
        }
        catch (IOException e) {
          Messages.showErrorDialog(project, e.toString(), IdeBundle.message("jar.build.error.title"));
        }
      }
    }, IdeBundle.message("jar.build.progress.title"), true, project);
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
        return IdeBundle.message("filter.all.file.types");
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
      manifest.getMainAttributes().putValue(MAIN_CLASS,mainClass);
    }

    // write temp file and rename it to the jar to avoid deployment of incomplete jar. SCR #30303
    final File tempFile = File.createTempFile("___"+ FileUtil.getNameWithoutExtension(jarFile), JAR_EXTENSION, jarFile.getParentFile());
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
            ProgressManager.getInstance().getProgressIndicator().setText2(
              IdeBundle.message("jar.build.processing.file.progress", presentablePath));
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
        String message = IdeBundle.message("jar.build.cannot.overwrite.error", FileUtil.toSystemDependentName(jarFile.getPath()),
                                           FileUtil.toSystemDependentName(tempFile.getPath()));
        Messages.showErrorDialog(module.getProject(), message, IdeBundle.message("jar.build.error.title"));
      }
    }
  }

  public void update(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    e.getPresentation().setEnabled(project != null);
  }
}
