package com.intellij.compiler.impl.javaCompiler;

import com.intellij.compiler.JikesOutputParser;
import com.intellij.compiler.JikesSettings;
import com.intellij.compiler.OutputParser;
import com.intellij.compiler.options.CompilerConfigurable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.StringTokenizer;

class JikesCompiler implements BackendCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.JikesHandler");
  private Project myProject;
  private File myTempFile;

  public JikesCompiler(Project project) {
    myProject = project;
  }

  public boolean checkCompiler() {
    String compilerPath = getCompilerPath();
    if (compilerPath == null) {
      Messages.showMessageDialog(
        myProject,
        CompilerBundle.message("jikes.error.path.to.compiler.unspecified"), CompilerBundle.message("compiler.jikes.name"),
        Messages.getErrorIcon()
      );
      openConfigurationDialog();
      compilerPath = getCompilerPath(); // update path
      if (compilerPath == null) {
        return false;
      }
    }

    File file = new File(compilerPath);
    if (!file.exists()) {
      Messages.showMessageDialog(
        myProject,
        CompilerBundle.message("jikes.error.path.to.compiler.missing", compilerPath),
        CompilerBundle.message("compiler.jikes.name"),
        Messages.getErrorIcon()
      );
      openConfigurationDialog();
      compilerPath = getCompilerPath(); // update path
      if (compilerPath == null) {
        return false;
      }
      if (!new File(compilerPath).exists()) {
        return false;
      }
    }

    return true;
  }

  private void openConfigurationDialog() {
    ShowSettingsUtil.getInstance().editConfigurable(myProject, CompilerConfigurable.getInstance(myProject));
  }

  public String getCompilerPath() {
    return JikesSettings.getInstance(myProject).JIKES_PATH.replace('/', File.separatorChar);
  }

  public OutputParser createOutputParser() {
    return new JikesOutputParser(myProject);
  }

  public String[] createStartupCommand(final ModuleChunk chunk, final CompileContext context, final String outputPath)
    throws IOException {

    final ArrayList commandLine = new ArrayList();
    final IOException[] ex = new IOException[]{null};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        try {
          _createStartupCommand(chunk, commandLine, outputPath);
        }
        catch (IOException e) {
          ex[0] = e;
        }
      }
    });
    if (ex[0] != null) {
      throw ex[0];
    }
    return (String[])commandLine.toArray(new String[commandLine.size()]);
  }

  private void _createStartupCommand(final ModuleChunk chunk, final ArrayList commandLine, final String outputPath) throws IOException {

    //noinspection HardCodedStringLiteral
    myTempFile = File.createTempFile("jikes", ".tmp");
    myTempFile.deleteOnExit();

    final VirtualFile[] files = chunk.getFilesToCompile();
    PrintWriter writer = new PrintWriter(new FileWriter(myTempFile));
    try {
      for (int i = 0; i < files.length; i++) {
        writer.println(files[i].getPath());
      }
    }
    finally {
      writer.close();
    }

    String compilerPath = getCompilerPath();
    LOG.assertTrue(compilerPath != null, "No path to compiler configured");

    commandLine.add(compilerPath);

    //noinspection HardCodedStringLiteral
    commandLine.add("-verbose");
    //noinspection HardCodedStringLiteral
    commandLine.add("-classpath");

    // must include output path to classpath, otherwise javac will compile all dependent files no matter were they compiled before or not
    commandLine.add(chunk.getCompilationBootClasspath() + File.pathSeparator + chunk.getCompilationClasspath());

    setupSourceVersion(chunk, commandLine);
    //noinspection HardCodedStringLiteral
    commandLine.add("-sourcepath");
    String sourcePath = chunk.getSourcePath();
    if (sourcePath.length() > 0) {
      commandLine.add(sourcePath);
    }
    else {
      commandLine.add("\"\"");
    }

    //noinspection HardCodedStringLiteral
    commandLine.add("-d");
    LOG.assertTrue(outputPath != null);
    commandLine.add(outputPath.replace('/', File.separatorChar));

    JikesSettings jikesSettings = JikesSettings.getInstance(myProject);
    StringTokenizer tokenizer = new StringTokenizer(jikesSettings.getOptionsString(), " ");
    while (tokenizer.hasMoreTokens()) {
      commandLine.add(tokenizer.nextToken());
    }
    commandLine.add("@" + myTempFile.getAbsolutePath());
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void setupSourceVersion(final ModuleChunk chunk, final ArrayList commandLine) {
    final ProjectJdk jdk = chunk.getJdk();
    final String versionString = jdk.getVersionString();

    final LanguageLevel applicableLanguageLevel = getApplicableLanguageLevel(versionString);
    if (applicableLanguageLevel.equals(LanguageLevel.JDK_1_5)) {
      commandLine.add("-source");
      commandLine.add("1.4"); // -source 1.5 not supported yet by jikes, so use the highest possible version
    }
    else if (applicableLanguageLevel.equals(LanguageLevel.JDK_1_4)) {
      commandLine.add("-source");
      commandLine.add("1.4");
    }
  }

  private LanguageLevel getApplicableLanguageLevel(String versionString) {
    LanguageLevel languageLevel = ProjectRootManagerEx.getInstanceEx(myProject).getLanguageLevel();

    if (LanguageLevel.JDK_1_5.equals(languageLevel)) {
      if (!(isOfVersion(versionString, "1.5") || isOfVersion(versionString, "5.0"))) {
        languageLevel = LanguageLevel.JDK_1_4;
      }
    }

    if (LanguageLevel.JDK_1_4.equals(languageLevel)) {
      if (!isOfVersion(versionString, "1.4") && !isOfVersion(versionString, "1.5") && !isOfVersion(versionString, "5.0")) {
        languageLevel = LanguageLevel.JDK_1_3;
      }
    }

    return languageLevel;
  }

  public void processTerminated() {
    if (myTempFile != null) {
      myTempFile.delete();
      myTempFile = null;
    }
  }

  private static boolean isOfVersion(String versionString, String checkedVersion) {
    return versionString.indexOf(checkedVersion) > -1;
  }
}

