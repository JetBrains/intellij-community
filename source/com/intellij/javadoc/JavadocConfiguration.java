package com.intellij.javadoc;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.RegexpFilter;
import com.intellij.execution.filters.TextConsoleBuidlerFactory;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ex.PathUtilEx;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ProjectRootsTraversing;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 24, 2004
 */
public class JavadocConfiguration implements RunProfile, JDOMExternalizable{
  public String OUTPUT_DIRECTORY;
  public String OPTION_SCOPE = "protected";
  public boolean OPTION_HIERARCHY = true;
  public boolean OPTION_NAVIGATOR = true;
  public boolean OPTION_INDEX = true;
  public boolean OPTION_SEPARATE_INDEX = true;
  public boolean OPTION_DOCUMENT_TAG_USE = false;
  public boolean OPTION_DOCUMENT_TAG_AUTHOR = false;
  public boolean OPTION_DOCUMENT_TAG_VERSION = false;
  public boolean OPTION_DOCUMENT_TAG_DEPRECATED = true;
  public boolean OPTION_DEPRECATED_LIST = true;
  public String OTHER_OPTIONS = "";
  public String HEAP_SIZE;
  public boolean OPEN_IN_BROWSER = true;

  private final Project myProject;
  private GenerationOptions myGenerationOptions;

  public static final class GenerationOptions {
    public final String packageFQName;
    public final PsiDirectory directoryFrom;
    public final boolean isGenerationForPackage;
    public final boolean isGenerationWithSubpackages;

    public GenerationOptions(String packageFQName, PsiDirectory directory, boolean generationForPackage, boolean generationWithSubpackages) {
      this.packageFQName = packageFQName;
      this.directoryFrom = directory;
      isGenerationForPackage = generationForPackage;
      isGenerationWithSubpackages = generationWithSubpackages;
    }
  }

  public void setGenerationOptions(GenerationOptions generationOptions) {
    myGenerationOptions = generationOptions;
  }

  public JavadocConfiguration(Project project) {
    myProject = project;
  }

  public RunProfileState getState(DataContext context,
                                  RunnerInfo runnerInfo,
                                  RunnerSettings runnerSettings,
                                  ConfigurationPerRunnerSettings configurationSettings) {
    return new MyJavaCommandLineState(myProject, myGenerationOptions);
  }

  public String getName() {
    return "Javadoc";
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    if (myGenerationOptions == null) {
      throw new RuntimeConfigurationError("Generation options for javadoc not specified");
    }
  }

  public JavadocConfigurable createConfigurable() {
    return new JavadocConfigurable(this);
  }

  public Module[] getModules() {
    return null;
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  private class MyJavaCommandLineState extends CommandLineState {
    private final GenerationOptions myGenerationOptions;
    private final Project myProject;

    public MyJavaCommandLineState(Project project, GenerationOptions generationOptions) {
      super(null, null);
      myGenerationOptions = generationOptions;
      myProject = project;
      TextConsoleBuilder builder = TextConsoleBuidlerFactory.getInstance().createBuilder(project);
      builder.addFilter(new RegexpFilter(project, "$FILE_PATH$:$LINE$:[^\\^]+\\^"));
      builder.addFilter(new RegexpFilter(project, "$FILE_PATH$:$LINE$: warning - .+$"));
      setConsoleBuilder(builder);
    }

    protected GeneralCommandLine createCommandLine() throws ExecutionException {
      final GeneralCommandLine cmdLine = new GeneralCommandLine();
      final ProjectJdk jdk = PathUtilEx.getAnyJdk(myProject);
      setupExeParams(jdk, cmdLine);
      setupProgramParameters(cmdLine);
      return cmdLine;
    }


    private void setupExeParams(final ProjectJdk jdk, GeneralCommandLine cmdLine) throws ExecutionException {
      final String jdkPath = jdk != null? PathUtil.getLocalPath(jdk.getHomeDirectory()) : null;
      if (jdkPath == null) {
        throw new CantRunException("JDK path is not specified\nCannot generate JavaDoc");
      }
      String versionString = jdk.getVersionString();
      if (HEAP_SIZE != null && HEAP_SIZE.trim().length() != 0) {
        if (versionString.indexOf("1.1") > -1) {
          cmdLine.getParametersList().prepend("-J-mx" + HEAP_SIZE + "m");
        }
        else {
          cmdLine.getParametersList().prepend("-J-Xmx" + HEAP_SIZE + "m");
        }
      }
      cmdLine.setWorkingDirectory(null);
      cmdLine.setExePath(jdkPath.replace('/', File.separatorChar) + File.separator + "bin" + File.separator + (SystemInfo.isWindows ? "javadoc.exe" : "javadoc"));
    }

    private void setupProgramParameters(final GeneralCommandLine cmdLine) throws CantRunException {
      final ParametersList parameters = cmdLine.getParametersList();

      if (OPTION_SCOPE != null) {
        parameters.add("-" + OPTION_SCOPE);
      }

      if (!OPTION_HIERARCHY) {
        parameters.add("-notree");
      }

      if (!OPTION_NAVIGATOR) {
        parameters.add("-nonavbar");
      }

      if (!OPTION_INDEX) {
        parameters.add("-noindex");
      }
      else if (OPTION_SEPARATE_INDEX) {
        parameters.add("-splitindex");
      }

      if (OPTION_DOCUMENT_TAG_USE) {
        parameters.add("-use");
      }

      if (OPTION_DOCUMENT_TAG_AUTHOR) {
        parameters.add("-author");
      }

      if (OPTION_DOCUMENT_TAG_VERSION) {
        parameters.add("-version");
      }

      if (!OPTION_DOCUMENT_TAG_DEPRECATED) {
        parameters.add("-nodeprecated");
      }
      else if (!OPTION_DEPRECATED_LIST) {
        parameters.add("-nodeprecatedlist");
      }

      parameters.addParametersString(OTHER_OPTIONS);

      String classPath = ProjectRootsTraversing.collectRoots(myProject, ProjectRootsTraversing.PROJECT_LIBRARIES).getPathsString();
      if (classPath.length() >0) {
        parameters.add("-classpath");
        parameters.add(classPath);
      }

      parameters.add("-sourcepath");
      parameters.add(ProjectRootsTraversing.collectRoots(myProject, ProjectRootsTraversing.PROJECT_SOURCES).getPathsString());

      if (OUTPUT_DIRECTORY != null) {
        parameters.add("-d");
        parameters.add(OUTPUT_DIRECTORY.replace('/', File.separatorChar));
      }

      final Collection<String> packages = new HashSet<String>();
      ApplicationManager.getApplication().runReadAction(
        new Runnable(){
          public void run() {
            FileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
            VirtualFile startingFile = null;
            if (myGenerationOptions.isGenerationForPackage) {
              if (!myGenerationOptions.isGenerationWithSubpackages) {
                packages.add(myGenerationOptions.packageFQName);
                return;
              }
              startingFile = myGenerationOptions.directoryFrom.getVirtualFile();
            }
            MyContentIterator contentIterator = new MyContentIterator(myProject, packages);
            if (startingFile == null) {
              fileIndex.iterateContent(contentIterator);
            }
            else {
              fileIndex.iterateContentUnderDirectory(startingFile, contentIterator);
            }
          }
        }
      );
      if (packages.size() == 0) {
        throw new CantRunException("Selected package(s) contain no Java classes");
      }
      parameters.addAll(new ArrayList<String>(packages));
    }

    protected OSProcessHandler startProcess() throws ExecutionException {
      final OSProcessHandler handler = super.startProcess();
      ProcessTerminatedListener.attach(handler, myProject, "\njavadoc exited with exit code $EXIT_CODE$\n");
      handler.addProcessListener(new ProcessAdapter() {
        public void processTerminated(ProcessEvent event) {
          if (!handler.isProcessTerminating() && OPEN_IN_BROWSER) {
            String url = OUTPUT_DIRECTORY + File.separator + "index.html";
            if (new File(url).exists() && event.getExitCode() == 0) {
              BrowserUtil.launchBrowser(url);
            }
          }
        }
      });
      return handler;
    }
  }

  private static class MyContentIterator implements ContentIterator {
    private final PsiManager myPsiManager;
    private final Collection<String> myPackages;

    public MyContentIterator(Project project, Collection<String> packages) {
      myPsiManager = PsiManager.getInstance(project);
      myPackages = packages;
    }

    public boolean processFile(VirtualFile fileOrDir) {
      if (!(fileOrDir.getFileSystem() instanceof LocalFileSystem)) {
        return true;
      }
      final PsiDirectory directory = getPsiDirectory(fileOrDir);
      if (directory == null) {
        if (!StdFileTypes.JAVA.equals(FileTypeManager.getInstance().getFileTypeByFile(fileOrDir))) {
          return true;
        }
        PsiPackage psiPackage = getPsiPackage(fileOrDir.getParent());
        if (psiPackage != null && psiPackage.getQualifiedName().length() == 0) {
          myPackages.add(PathUtil.getLocalPath(fileOrDir));
        }
        return true;
      }
      else if (!dirContainsJavaFiles(directory)) {
        return true;
      }
      PsiPackage psiPackage = directory.getPackage();
      if (psiPackage == null) {
        return true;
      }
      myPackages.add(psiPackage.getQualifiedName());
      return true;
    }

    private PsiDirectory getPsiDirectory(VirtualFile fileOrDir) {
      return fileOrDir != null ? myPsiManager.findDirectory(fileOrDir) : null;
    }

    private PsiPackage getPsiPackage(VirtualFile dir) {
      PsiDirectory directory = getPsiDirectory(dir);
      return directory != null ? directory.getPackage() : null;
    }
  }

  private static boolean dirContainsJavaFiles(PsiDirectory dir) {
    PsiFile[] files = dir.getFiles();
    for(int i = 0; i < files.length; i++){
      if (files[i] instanceof PsiJavaFile) {
        return true;
      }
    }
    return false;
  }
}
