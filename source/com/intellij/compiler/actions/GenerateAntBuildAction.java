package com.intellij.compiler.actions;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.ant.*;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class GenerateAntBuildAction extends CompileActionBase {

  protected void doAction(DataContext dataContext, final Project project) {
    CompilerConfiguration.getInstance(project).convertPatterns();
    final GenerateAntBuildDialog dialog = new GenerateAntBuildDialog(project);
    dialog.show();
    if (dialog.isOK()) {
      String[] names = dialog.getRepresentativeModuleNames();
      final GenerationOptions genOptions = new GenerationOptions(project, dialog.isGenerateSingleFileBuild(), dialog.isFormsCompilationEnabled(), dialog.isBackupFiles(), names);
      generate(project, genOptions);
    }
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    presentation.setEnabled(project != null);
  }

  private void generate(final Project project, final GenerationOptions genOptions) {
    final List<File> filesToRefresh = new ArrayList<File>();
    try {
      final File[] generated;
      if (genOptions.generateSingleFile) {
        generated = generateSingleFileBuild(project, genOptions, filesToRefresh);
      }
      else {
        generated = generateMultipleFileBuild(project, genOptions, filesToRefresh);
      }
      if (generated != null) {
        StringBuffer filesString = new StringBuffer();
        for (int idx = 0; idx < generated.length; idx++) {
          final File file = generated[idx];
          if (idx > 0) {
            filesString.append(",\n");
          }
          filesString.append(file.getPath());
        }
        Messages.showInfoMessage(project, "Ant build files successfully generated:\n" + filesString.toString(), "Generate Ant Build");
      }
    }
    catch (IOException e) {
      Messages.showErrorDialog(project, "Failed to generate ant build script: " + e.getMessage(), "Generate Ant Build");
    }
    finally {
      if (filesToRefresh.size() > 0) {
        CompilerUtil.refreshIOFiles(filesToRefresh.toArray(new File[filesToRefresh.size()]));
      }
    }
  }

  private boolean backup(final File file, final Project project, GenerationOptions genOptions, List<File> filesToRefresh) {
    if (!genOptions.backupPreviouslyGeneratedFiles || !file.exists()) {
      return true;
    }
    final String path = file.getPath();
    final int extensionIndex = path.lastIndexOf(".");
    final String extension = path.substring(extensionIndex, path.length());
    final String backupPath = path.substring(0, extensionIndex) + "_" + new Date(file.lastModified()).toString().replaceAll("\\s+", "_").replaceAll(":", "-") + extension;
    final File backupFile = new File(backupPath);
    final boolean ok = file.renameTo(backupFile);
    if (!ok) {
      Messages.showErrorDialog(project, "Failed to backup file " + path, "Backup Error");
    }
    filesToRefresh.add(backupFile);
    return ok;
  }

  public File[] generateSingleFileBuild(Project project, GenerationOptions genOptions, List<File> filesToRefresh) throws IOException {
    final File projectBuildFileDestDir = VfsUtil.virtualToIoFile(project.getProjectFile().getParent());
    projectBuildFileDestDir.mkdirs();
    final File destFile = new File(projectBuildFileDestDir, BuildProperties.getProjectBuildFileName(project) + ".xml");
    final File propertiesFile = new File(projectBuildFileDestDir, BuildProperties.getPropertyFileName(project));

    if (!backup(destFile, project, genOptions, filesToRefresh)) {
      return null;
    }
    if (!backup(propertiesFile, project, genOptions, filesToRefresh)) {
      return null;
    }

    destFile.createNewFile();
    propertiesFile.createNewFile();
    final DataOutputStream dataOutput = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(destFile)));
    try {
      new SingleFileProjectBuild(project, genOptions).generate(dataOutput);
    }
    finally {
      dataOutput.close();
    }
    final DataOutputStream propertiesOut = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(propertiesFile)));
    try {
      new PropertyFileGenerator(project).generate(propertiesOut);
    }
    finally {
      propertiesOut.close();
    }
    filesToRefresh.add(destFile);
    filesToRefresh.add(propertiesFile);
    return new File[] {destFile, propertiesFile};
  }

  public File[] generateMultipleFileBuild(Project project, GenerationOptions genOptions, List<File> filesToRefresh) throws IOException {
    final File projectBuildFileDestDir = VfsUtil.virtualToIoFile(project.getProjectFile().getParent());
    projectBuildFileDestDir.mkdirs();
    final List<File> generated = new ArrayList<File>();
    final File projectBuildFile = new File(projectBuildFileDestDir, BuildProperties.getProjectBuildFileName(project) + ".xml");
    final File propertiesFile = new File(projectBuildFileDestDir, BuildProperties.getPropertyFileName(project));

    if (!backup(projectBuildFile, project, genOptions, filesToRefresh)) {
      return null;
    }
    if (!backup(propertiesFile, project, genOptions, filesToRefresh)) {
      return null;
    }

    projectBuildFile.createNewFile();
    final DataOutputStream mainDataOutput = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(projectBuildFile)));
    try {

      final MultipleFileProjectBuild build = new MultipleFileProjectBuild(project, genOptions);
      build.generate(mainDataOutput);
      generated.add(projectBuildFile);

      // the sequence in which modules are imported is important cause output path properties for dependent modules should be defined first
      ModuleChunk[] chunks = genOptions.getModuleChunks();

      for (int idx = 0; idx < chunks.length; idx++) {
        final ModuleChunk chunk = chunks[idx];
        final File chunkBaseDir = BuildProperties.getModuleChunkBaseDir(chunk);
        chunkBaseDir.mkdirs();
        final File chunkBuildFile = new File(chunkBaseDir, BuildProperties.getModuleChunkBuildFileName(chunk) + ".xml");

        final boolean moduleBackupOk = backup(chunkBuildFile, project, genOptions, filesToRefresh);
        if (!moduleBackupOk) {
          return null;
        }

        chunkBuildFile.createNewFile();
        final DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(chunkBuildFile)));
        try {
          new ModuleChunkAntProject(project, chunk, genOptions).generate(out);
          generated.add(chunkBuildFile);
        }
        finally {
          out.close();
        }
      }
    }
    finally {
      mainDataOutput.close();
    }
    // properties
    final DataOutputStream propertiesOut = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(propertiesFile)));
    try {
      new PropertyFileGenerator(project).generate(propertiesOut);
      generated.add(propertiesFile);
    }
    finally {
      propertiesOut.close();
    }

    filesToRefresh.addAll(generated);
    return generated.toArray(new File[generated.size()]);
  }

}