/*
 * @author: Eugene Zhuravlev
 * Date: Jan 17, 2003
 * Time: 1:42:26 PM
 */
package com.intellij.compiler.impl;

import com.intellij.analysis.AnalysisScope;
import com.intellij.compiler.*;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.compiler.make.DependencyCache;
import com.intellij.compiler.make.MakeUtil;
import com.intellij.compiler.progress.CompilerProgressIndicator;
import com.intellij.j2ee.make.impl.MakeUtilImpl;
import com.intellij.j2ee.module.J2EEModuleUtilEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.Compiler;
import com.intellij.openapi.compiler.ex.CompilerPathsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ui.configuration.ContentEntriesEditor;
import com.intellij.openapi.roots.ui.configuration.LibrariesEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.ForwardDependenciesBuilder;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ProfilingUtil;
import gnu.trove.THashMap;
import gnu.trove.TObjectProcedure;

import java.io.*;
import java.util.*;

public class CompileDriver {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.CompileDriver");

  private final Project myProject;
  private final Map myCompilerToCacheMap = new com.intellij.util.containers.HashMap();
  private Map<Pair<Compiler, Module>, VirtualFile> myGenerationCompilerModuleToOutputDirMap;
  private String myCachesDirectoryPath;
  private Set<String> myOutputFilesOnDisk = null;
  private boolean myShouldClearOutputDirectory;

  private Map<Module, String> myModuleOutputPaths = new HashMap<Module, String>();
  private Map<Module, String> myModuleTestOutputPaths = new HashMap<Module, String>();

  private CompileDriver.ExitStatus myExitStatus = null;
  private ProjectRootManager myProjectRootManager;
  private static final String VERSION_FILE_NAME = "version.dat";
  private static final String LOCK_FILE_NAME = "in_progress.dat";
  private final FileProcessingCompilerAdapterFactory myProcessingCompilerAdapterFactory;
  private final FileProcessingCompilerAdapterFactory myPackagingCompilerAdapterFactory;
  final ProjectCompileScope myProjectCompileScope;

  public CompileDriver(Project project) {
    myProject = project;
    myCachesDirectoryPath = CompilerPaths.getCacheStoreDirectory(myProject).getPath().replace('/', File.separatorChar);
    myShouldClearOutputDirectory = CompilerConfiguration.getInstance(myProject).isClearOutputDirectory();

    myGenerationCompilerModuleToOutputDirMap =
    new com.intellij.util.containers.HashMap<Pair<Compiler, Module>, VirtualFile>();

    final Compiler[] compilers = CompilerManager.getInstance(myProject).getCompilers(GeneratingCompiler.class);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final Module[] allModules = ModuleManager.getInstance(myProject).getModules();
        for (int idx = 0; idx < compilers.length; idx++) {
          GeneratingCompiler compiler = (GeneratingCompiler)compilers[idx];
          for (int i = 0; i < allModules.length; i++) {
            final Module module = allModules[i];
            final String path = getGenerationOutputPath(compiler, module);
            final File file = new File(path);
            final VirtualFile vFile;
            if (file.mkdirs()) {
              vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
            }
            else {
              vFile = LocalFileSystem.getInstance().findFileByPath(path);
            }
            Pair<Compiler, Module> pair = new Pair<Compiler, Module>(compiler, module);
            myGenerationCompilerModuleToOutputDirMap.put(pair, vFile);
          }
        }
      }
    });

    myProjectRootManager = ProjectRootManager.getInstance(myProject);
    myProcessingCompilerAdapterFactory = new FileProcessingCompilerAdapterFactory() {
      public FileProcessingCompilerAdapter create(CompileContext context, FileProcessingCompiler compiler) {
        return new FileProcessingCompilerAdapter(context, compiler);
      }
    };
    myPackagingCompilerAdapterFactory = new FileProcessingCompilerAdapterFactory() {
      public FileProcessingCompilerAdapter create(CompileContext context, FileProcessingCompiler compiler) {
        return new PackagingCompilerAdapter(context, (PackagingCompiler)compiler);
      }
    };
    myProjectCompileScope = new ProjectCompileScope(myProject);
  }

  public void rebuild(CompileStatusNotification callback) {
    doRebuild(callback, null, true, addAdditionalRoots(myProjectCompileScope));
  }

  public void make(CompileStatusNotification callback) {
    make(myProjectCompileScope, callback);
    /*
    class CallbackWrapper implements CompileStatusNotification {
      private int myErrorCount = 0;
      private boolean myLastCompilationAborted = false;
      private final CompileStatusNotification myDelegate;

      public CallbackWrapper(CompileStatusNotification delegate) {
        myDelegate = delegate;
      }

      public void finished(boolean aborted, int errors, int warnings) {
        myErrorCount += errors;
        myLastCompilationAborted = aborted;
        myDelegate.finished(aborted, errors, warnings);
      }

      public int getErrorCount() {
        return myErrorCount;
      }

      public boolean isLastCompilationAborted() {
        return myLastCompilationAborted;
      }
    };
    final CallbackWrapper callbackWrapper = new CallbackWrapper(callback);
    final List<Chunk<Module>> chunks = ModuleCompilerUtil.getSortedModuleChunks(myProject, ModuleManager.getInstance(myProject).getModules());
    for (Iterator<Chunk<Module>> it = chunks.iterator(); it.hasNext();) {
      final Chunk<Module> chunk = it.next();
      final Set<Module> modules = chunk.getNodes();
      if (modules.size() == 1) {
        make(new ModuleCompileScope(modules.iterator().next(), false), callbackWrapper);
      }
      else {
        final CompileScope[] scopes = new CompileScope[modules.size()];
        int scopeIndex = 0;
        for (Iterator<Module> moduleIterator = modules.iterator(); moduleIterator.hasNext();) {
          final Module module = moduleIterator.next();
          scopes[scopeIndex++] = new ModuleCompileScope(module, false);
        }
        make(new CompositeScope(scopes), callbackWrapper);
      }
      if (callbackWrapper.isLastCompilationAborted() || callbackWrapper.getErrorCount() > 0) {
        break;
      }
    }
    */
  }

  public void make(Project project, Module[] modules, CompileStatusNotification callback) {
    make(new ModuleCompileScope(project, modules, true), callback);
  }

  public void make(Module module, CompileStatusNotification callback) {
    make(new ModuleCompileScope(module, true), callback);
  }

  public void make(CompileScope scope, CompileStatusNotification callback) {
    scope = addAdditionalRoots(scope);
    if (validateCompilerConfiguration(scope, false)) {
      startup(scope, false, false, callback, null, true);
    }
  }

  public void compile(CompileScope scope, CompileStatusNotification callback, boolean trackDependencies) {
    if (trackDependencies) {
      scope = new TrackDependenciesScope(scope);
    }
    if (validateCompilerConfiguration(scope, false)) {
      startup(scope, false, true, callback, null, true);
    }
  }

  private static class CompileStatus {
    final int CACHE_FORMAT_VERSION;
    final boolean COMPILATION_IN_PROGRESS;

    public CompileStatus(int cacheVersion, boolean isCompilationInProgress) {
      this.CACHE_FORMAT_VERSION = cacheVersion;
      this.COMPILATION_IN_PROGRESS = isCompilationInProgress;
    }
  }

  private CompileStatus readStatus() {
    final boolean isInProgress = new File(myCachesDirectoryPath, LOCK_FILE_NAME).exists();
    int version = -1;
    try {
      final File versionFile = new File(myCachesDirectoryPath, VERSION_FILE_NAME);
      if (versionFile.exists()) {
        DataInputStream in = new DataInputStream(new FileInputStream(versionFile));
        try {
          version = in.readInt();
        }
        finally {
          in.close();
        }
      }
    }
    catch (IOException e) {
      LOG.info(e);  // may happen in case of IDEA crashed and the file is not written properly
      return null;
    }
    return new CompileStatus(version, isInProgress);
  }

  private void writeStatus(CompileStatus status, CompileContext context) {
    final File statusFile = new File(myCachesDirectoryPath, VERSION_FILE_NAME);
    final File lockFile = new File(myCachesDirectoryPath, LOCK_FILE_NAME);
    try {
      if (!statusFile.exists()) {
        statusFile.createNewFile();
      }
      DataOutputStream out = new DataOutputStream(new FileOutputStream(statusFile));
      try {
        out.writeInt(status.CACHE_FORMAT_VERSION);
      }
      finally {
        out.close();
      }
      if (status.COMPILATION_IN_PROGRESS) {
        lockFile.createNewFile();
      }
      else {
        lockFile.delete();
      }
    }
    catch (IOException e) {
      context.addMessage(CompilerMessageCategory.ERROR, "Error: " + e.getMessage(), null, -1, -1);
    }
  }

  private void doRebuild(CompileStatusNotification callback, CompilerMessage message, final boolean checkCachesVersion, final CompileScope compileScope) {
    if (validateCompilerConfiguration(compileScope, true)) {
      startup(compileScope, true, false, callback, message, checkCachesVersion);
    }
  }

  private CompileScope addAdditionalRoots(CompileScope originalScope) {
    CompileScope scope = originalScope;
    for (Iterator<Pair<Compiler, Module>> it = myGenerationCompilerModuleToOutputDirMap.keySet().iterator(); it.hasNext();) {
      final Pair<Compiler, Module> pair = it.next();
      final VirtualFile outputDir = myGenerationCompilerModuleToOutputDirMap.get(pair);
      scope = new CompositeScope(scope, new FileSetCompileScope(new VirtualFile[] {outputDir}, new Module[]{pair.getSecond()}));
    }
    CompileScope additionalJ2eeScope = MakeUtilImpl.getOutOfSourceJ2eeCompileScope(scope);
    if (additionalJ2eeScope != null) {
      scope = new CompositeScope(scope, additionalJ2eeScope);
    }
    return scope;
  }

  private void startup(final CompileScope scope, final boolean isRebuild, final boolean forceCompile, final CompileStatusNotification callback, CompilerMessage message, final boolean checkCachesVersion) {
    final CompilerProgressIndicator indicator = new CompilerProgressIndicator(
      myProject,
      CompilerWorkspaceConfiguration.getInstance(myProject).COMPILE_IN_BACKGROUND,
      forceCompile ? "Compile" : "Make"
    );
    WindowManager.getInstance().getStatusBar(myProject).setInfo("");

    final CompileContextImpl compileContext = new CompileContextImpl(myProject, indicator, scope, new DependencyCache(myCachesDirectoryPath), this, (!isRebuild && !forceCompile));
    for (Iterator<Pair<Compiler, Module>> it = myGenerationCompilerModuleToOutputDirMap.keySet().iterator(); it.hasNext();) {
      Pair<Compiler, Module> pair = it.next();
      compileContext.assignModule(myGenerationCompilerModuleToOutputDirMap.get(pair), pair.getSecond());
    }

    if (message != null) {
      compileContext.addMessage(message);
    }

    FileDocumentManager.getInstance().saveAllDocuments();

    final Thread compileThread = new Thread("Compile Thread") {
      public void run() {
        synchronized (CompilerManager.getInstance(myProject)) {
          ProgressManager.getInstance().runProcess(new Runnable() {
            public void run() {
              try {
                if (LOG.isDebugEnabled()) {
                  LOG.debug("COMPILATION STARTED");
                }
                doCompile(compileContext, isRebuild, forceCompile, callback, checkCachesVersion);
              }
              finally {
                if (LOG.isDebugEnabled()) {
                  LOG.debug("COMPILATION FINISHED");
                }
              }
            }
          }, compileContext.getProgressIndicator());
        }
      }
    };
    compileThread.setPriority(Thread.NORM_PRIORITY);
    compileThread.start();
  }

  private void doCompile(final CompileContextImpl compileContext,
                         final boolean isRebuild,
                         final boolean forceCompile,
                         final CompileStatusNotification callback, final boolean checkCachesVersion) {
    ExitStatus status = ExitStatus.ERRORS;
    boolean wereExceptions = false;
    try {
      compileContext.getProgressIndicator().pushState();
      if (checkCachesVersion) {
        final CompileStatus compileStatus = readStatus();
        if (compileStatus == null) {
          compileContext.requestRebuildNextTime("Compiler caches are corrupted. Starting rebuild...");
        }
        else if (compileStatus.CACHE_FORMAT_VERSION != -1 && compileStatus.CACHE_FORMAT_VERSION != CompilerConfiguration.DEPENDENCY_FORMAT_VERSION) {
          compileContext.requestRebuildNextTime("Compiler caches on disk have old format. Starting rebuild...");
        }
        else if (compileStatus.COMPILATION_IN_PROGRESS) {
          compileContext.requestRebuildNextTime("Previous compilation did not terminate properly. Caches may have been corrupted. Starting rebuild...");
        }
        if (compileContext.isRebuildRequested()) {
          return;
        }
      }
      writeStatus(new CompileStatus(CompilerConfiguration.DEPENDENCY_FORMAT_VERSION, true), compileContext);
      if (compileContext.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
        return;
      }
      status = doCompile(compileContext, isRebuild, forceCompile);
    }
    catch (Throwable ex) {
      wereExceptions = true;
      throw new RuntimeException(ex);
    }
    finally {
      compileContext.getProgressIndicator().popState();
      final ExitStatus _status = status;
      if (compileContext.isRebuildRequested()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            doRebuild(
              callback,
              new CompilerMessageImpl(myProject, CompilerMessageCategory.INFORMATION, compileContext.getRebuildReason(), null, -1, -1), false, compileContext.getCompileScope()
            );
          }
        });
      }
      else {
        writeStatus(new CompileStatus(CompilerConfiguration.DEPENDENCY_FORMAT_VERSION, wereExceptions), compileContext);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            final int errorCount = compileContext.getMessageCount(CompilerMessageCategory.ERROR);
            final int warningCount = compileContext.getMessageCount(CompilerMessageCategory.WARNING);
            final String statusMessage = createStatusMessage(_status, warningCount, errorCount);
            final StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
            if (statusBar != null) { // because this code is in invoke later, the code may work for already closed project
              // in case another project was opened in the frame while the compiler was working (See SCR# 28591)
              statusBar.setInfo(statusMessage);
            }
            if (_status != ExitStatus.UP_TO_DATE && compileContext.getMessageCount(null) > 0) {
              compileContext.addMessage(CompilerMessageCategory.INFORMATION, statusMessage, null, -1, -1);
            }
            if (callback != null) {
              callback.finished(_status == ExitStatus.CANCELLED, errorCount, warningCount);
            }

            ProfilingUtil.operationFinished("make");
          }
        }, ModalityState.NON_MMODAL);
      }
    }
  }

  private static String createStatusMessage(final ExitStatus _status, final int warningCount, final int errorCount) {
    if (_status == ExitStatus.CANCELLED) {
      return "Compilation aborted";
    }
    if (_status == ExitStatus.UP_TO_DATE) {
      return "All files are up-to-date";
    }
    if (_status == ExitStatus.SUCCESS) {
      return (warningCount > 0)
             ? "Compilation completed successfully with " + warningCount + " warnings"
             : "Compilation completed successfully";
    }
    return "Compilation completed with " + errorCount + " errors and " + warningCount +
           (warningCount == 1 ? " warning" : " warnings");
  }

  private static class ExitStatus {
    private String myName;

    private ExitStatus(String name) {
      myName = name;
    }

    public String toString() {
      return myName;
    }

    public static final ExitStatus CANCELLED = new ExitStatus("CANCELLED");
    public static final ExitStatus ERRORS = new ExitStatus("ERRORS");
    public static final ExitStatus SUCCESS = new ExitStatus("SUCCESS");
    public static final ExitStatus UP_TO_DATE = new ExitStatus("UP_TO_DATE");
  }

  private ExitStatus doCompile(CompileContextImpl context, boolean isRebuild, final boolean forceCompile) {
    try {
      if (isRebuild) {
        deleteAll(context);
        if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
          return ExitStatus.ERRORS;
        }
      }

      try {
        context.getProgressIndicator().pushState();
        if (!executeCompileTasks(context, true)) {
          return ExitStatus.CANCELLED;
        }
      }
      finally {
        context.getProgressIndicator().popState();
      }

      if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
        return ExitStatus.ERRORS;
      }

      if (!isRebuild) {
        // compile tasks may change the contents of the output dirs so it is more safe to gather output files here
        context.getProgressIndicator().setText("Scanning output directories...");
        myOutputFilesOnDisk = CompilerPathsEx.getOutputFiles(myProject);
      }

      boolean didSomething = false;

      final CompilerManager compilerManager = CompilerManager.getInstance(myProject);

      didSomething |= generateSources(compilerManager, context, forceCompile);
      if (myExitStatus != null) {
        return myExitStatus;
      }

      didSomething |= invokeFileProcessingCompilers(compilerManager, context, SourceInstrumentingCompiler.class, myProcessingCompilerAdapterFactory, isRebuild);
      if (myExitStatus != null) {
        return myExitStatus;
      }

      try {
        didSomething |= translate(context, compilerManager, forceCompile, isRebuild);
        if (myExitStatus != null) {
          return myExitStatus;
        }

        didSomething |= invokeFileProcessingCompilers(compilerManager, context, ClassInstrumentingCompiler.class, myProcessingCompilerAdapterFactory, isRebuild);
        if (myExitStatus != null) {
          return myExitStatus;
        }

        // explicitly passing forceCompile = false because in scopes that is narrower than ProjectScope it is impossible
        // to understand whether the class to be processed is in scope or not. Otherwise compiler may process its items even if 
        // there were changes in completely independent files.
        didSomething |= invokeFileProcessingCompilers(compilerManager, context, ClassPostProcessingCompiler.class, myProcessingCompilerAdapterFactory, isRebuild);
        if (myExitStatus != null) {
          return myExitStatus;
        }

        didSomething |= invokeFileProcessingCompilers(compilerManager, context, PackagingCompiler.class, myPackagingCompilerAdapterFactory, isRebuild);
        if (myExitStatus != null) {
          return myExitStatus;
        }

        didSomething |= invokeFileProcessingCompilers(compilerManager, context, Validator.class, myProcessingCompilerAdapterFactory, isRebuild);
        if (myExitStatus != null) {
          return myExitStatus;
        }
      }
      finally {                                               
        context.getProgressIndicator().setText("Saving caches...");
        context.getDependencyCache().dispose();
      }

      try {
        context.getProgressIndicator().pushState();
        if (!executeCompileTasks(context, false)) {
          return ExitStatus.CANCELLED;
        }
      }
      finally {
        context.getProgressIndicator().popState();
      }

      if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
        return ExitStatus.ERRORS;
      }
      if (!didSomething) {
        return ExitStatus.UP_TO_DATE;
      }
      return ExitStatus.SUCCESS;
    }
    catch (ProcessCanceledException e) {
      return ExitStatus.CANCELLED;
    }
  }

  private boolean generateSources(final CompilerManager compilerManager, CompileContextImpl context, final boolean forceCompile) {
    boolean didSomething = false;

    final Compiler[] sourceGenerators = compilerManager.getCompilers(SourceGeneratingCompiler.class);
    for (int idx = 0; idx < sourceGenerators.length; idx++) {
      if (context.getProgressIndicator().isCanceled()) {
        myExitStatus = ExitStatus.CANCELLED;
        return false;
      }
      Compiler compiler = sourceGenerators[idx];
      final boolean generatedSomething = generateOutput(context, (SourceGeneratingCompiler)compiler, forceCompile);
      if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
        myExitStatus = ExitStatus.ERRORS;
        return false;
      }
      didSomething |= generatedSomething;
    }
    return didSomething;
  }

  private boolean translate(final CompileContextImpl context, final CompilerManager compilerManager, final boolean forceCompile, boolean isRebuild) {

    boolean didSomething = false;

    final Compiler[] translators = compilerManager.getCompilers(TranslatingCompiler.class);
    final VfsSnapshot snapshot = ApplicationManager.getApplication().runReadAction(new Computable<VfsSnapshot>() {
      public VfsSnapshot compute() {
        return new VfsSnapshot(context.getCompileScope().getFiles(null, true));
      }
    });

    for (int idx = 0; idx < translators.length; idx++) {
      if (context.getProgressIndicator().isCanceled()) {
        myExitStatus = ExitStatus.CANCELLED;
        return false;
      }

      final boolean compiledSomething;
      compiledSomething = compileSources(context, snapshot, (TranslatingCompiler)translators[idx], forceCompile, isRebuild);
      if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
        myExitStatus =  ExitStatus.ERRORS;
        return false;
      }

      didSomething |= compiledSomething;
    }
    return didSomething;
  }

  private static interface FileProcessingCompilerAdapterFactory {
    FileProcessingCompilerAdapter create(CompileContext context, FileProcessingCompiler compiler);
  }

  private boolean invokeFileProcessingCompilers(final CompilerManager compilerManager, CompileContextImpl context, Class fileProcessingCompilerClass, FileProcessingCompilerAdapterFactory factory, boolean forceCompile) {
    LOG.assertTrue(FileProcessingCompiler.class.isAssignableFrom(fileProcessingCompilerClass));
    boolean didSomething = false;
    final Compiler[] compilers = compilerManager.getCompilers(fileProcessingCompilerClass);
    if (compilers.length > 0) {
      try {
        for (int idx = 0; idx < compilers.length; idx++) {
          if (context.getProgressIndicator().isCanceled()) {
            myExitStatus =  ExitStatus.CANCELLED;
            return false;
          }

          final boolean processedSomething = processFiles(factory.create(context, (FileProcessingCompiler)compilers[idx]), forceCompile);

          if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
            myExitStatus = ExitStatus.ERRORS;
            return false;
          }

          didSomething |= processedSomething;
        }
      }
      catch(ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        context.addMessage(CompilerMessageCategory.ERROR, "Error processing classes: " + e.getMessage(), null, -1, -1);
        LOG.error(e);
      }
    }

    return didSomething;
  }

  private Map<Module, Set<GeneratingCompiler.GenerationItem>> buildModuleToGenerationItemMap(
    GeneratingCompiler.GenerationItem[] items) {
    final Map<Module, Set<GeneratingCompiler.GenerationItem>> map = new com.intellij.util.containers.HashMap<Module, Set<GeneratingCompiler.GenerationItem>>();
    for (int idx = 0; idx < items.length; idx++) {
      GeneratingCompiler.GenerationItem item = items[idx];
      Module module = item.getModule();
      LOG.assertTrue(module != null);
      Set<GeneratingCompiler.GenerationItem> itemSet = map.get(module);
      if (itemSet == null) {
        itemSet = new HashSet<GeneratingCompiler.GenerationItem>();
        map.put(module, itemSet);
      }
      itemSet.add(item);
    }
    return map;
  }

  private void deleteAll(final CompileContext context) {
    context.getProgressIndicator().pushState();
    try {
      final com.intellij.openapi.compiler.Compiler[] allCompilers = CompilerManager.getInstance(myProject).getCompilers(Compiler.class);
      context.getProgressIndicator().setText("Clearing output directories...");
      for (int idx = 0; idx < allCompilers.length; idx++) {
        final Compiler compiler = allCompilers[idx];
        if (compiler instanceof GeneratingCompiler) {
          final StateCache<ValidityState> cache = getGeneratingCompilerCache((GeneratingCompiler)compiler);
          if (!myShouldClearOutputDirectory) {
            deleteUrls(cache.getUrlsIterator());
          }
          cache.wipe();
        }
        else if (compiler instanceof FileProcessingCompiler) {
          final FileProcessingCompilerStateCache cache = getFileProcessingCompilerCache((FileProcessingCompiler)compiler);
          cache.wipe();
        }
        else if (compiler instanceof TranslatingCompiler) {
          final TranslatingCompilerStateCache cache = getTranslatingCompilerCache((TranslatingCompiler)compiler);
          if (!myShouldClearOutputDirectory) {
            deleteUrls(cache.getOutputUrlsIterator()); 
          }
          cache.wipe();
        }
      }
      if (myShouldClearOutputDirectory) {
        final File[] files = getAllOutputDirectories();
        for (int i = 0; i < files.length; i++) {
          deleteAllFilesIn(files[i]);
        }
        // ensure output directories exist, create and refresh if not exist
        final List<File> createdFiles = new ArrayList<File>(files.length);
        for (int idx = 0; idx < files.length; idx++) {
          final File file = files[idx];
          if (file.mkdirs()) {
            createdFiles.add(file);
          }
        }
        if (createdFiles.size() > 0) {
          CompilerUtil.refreshIOFiles(createdFiles.toArray(new File[createdFiles.size()]));
        }
      }

      clearCompilerSystemDirectory(context);
    }
    finally {
      context.getProgressIndicator().popState();
    }
  }

  private void deleteUrls(final Iterator<String> urlIterator) {
    while(urlIterator.hasNext()) {
      final String url = urlIterator.next();
      new File(VirtualFileManager.extractPath(url)).delete();
    }
  }

  private File[] getAllOutputDirectories() {
    final List<File> outputDirs = new ArrayList<File>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final VirtualFile[] outputDirectories = CompilerPathsEx.getOutputDirectories(
          ModuleManager.getInstance(myProject).getModules());
        for (int idx = 0; idx < outputDirectories.length; idx++) {
          final File directory = VfsUtil.virtualToIoFile(outputDirectories[idx]);
          outputDirs.add(directory);
        }
      }
    });

    return outputDirs.toArray(new File[outputDirs.size()]);
  }

  private void deleteAllFilesIn(File directory) {
    FileUtil.asyncDelete(directory);
    directory.mkdir();
  }

  private void clearCompilerSystemDirectory(final CompileContext context) {
    final File[] children = new File(myCachesDirectoryPath).listFiles();
    if (children != null) {
      for (int idx = 0; idx < children.length; idx++) {
        final File child = children[idx];
        final boolean deleteOk = FileUtil.delete(child);
        if (!deleteOk) {
          context.addMessage(CompilerMessageCategory.ERROR, "Failed to delete " + child.getPath(), null, -1, -1);
        }
      }
    }
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (Iterator<Pair<Compiler, Module>> it = myGenerationCompilerModuleToOutputDirMap.keySet().iterator(); it.hasNext();) {
          Pair<Compiler, Module> pair = it.next();
          final VirtualFile dir = myGenerationCompilerModuleToOutputDirMap.get(pair);
          final File[] files = VfsUtil.virtualToIoFile(dir).listFiles();
          if (files != null) {
            for (int idx = 0; idx < files.length; idx++) {
              final File file = files[idx];
              final boolean deleteOk = FileUtil.delete(file);
              if (!deleteOk) {
                context.addMessage(CompilerMessageCategory.ERROR, "Failed to delete " + file.getPath(), null, -1, -1);
              }
            }
          }
        }
      }
    });
  }

  private VirtualFile getGenerationOutputDir(final GeneratingCompiler compiler, final Module module) {
    return myGenerationCompilerModuleToOutputDirMap.get(new Pair<Compiler, Module>(compiler, module));
  }

  private static String getGenerationOutputPath(GeneratingCompiler compiler, Module module) {
    final String generatedCompilerDirectoryPath = CompilerPaths.getGeneratedDataDirectory(module.getProject(), compiler).getPath();
    return generatedCompilerDirectoryPath.replace(File.separatorChar, '/') + "/" +
           (module.getName().replace(' ', '_') + "." + Integer.toHexString(module.getModuleFilePath().hashCode()));
  }

  private boolean generateOutput(final CompileContextImpl context, final GeneratingCompiler compiler, final boolean forceGenerate) {
    final GeneratingCompiler.GenerationItem[] allItems = compiler.getGenerationItems(context);
    final List<GeneratingCompiler.GenerationItem> toGenerate = new ArrayList<GeneratingCompiler.GenerationItem>();
    final StateCache<ValidityState> cache = getGeneratingCompilerCache(compiler);
    final Set<String> pathsToRemove = new HashSet<String>(Arrays.asList(cache.getUrls()));

    final Map<GeneratingCompiler.GenerationItem, String> itemToOutputPathMap = new com.intellij.util.containers.HashMap<GeneratingCompiler.GenerationItem, String>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (int idx = 0; idx < allItems.length; idx++) {
          final GeneratingCompiler.GenerationItem item = allItems[idx];

          final Module itemModule = item.getModule();
          final String outputDirPath = getGenerationOutputPath(compiler, itemModule);
          final String outputPath = outputDirPath + "/" + item.getPath();
          itemToOutputPathMap.put(item, outputPath);

          final ValidityState savedState = cache.getState(outputPath);

          if (forceGenerate || savedState == null || !savedState.equalsTo(item.getValidityState())) {
            toGenerate.add(item);
          }
          else {
            pathsToRemove.remove(outputPath);
          }
        }
      }
    });

    final List<File> filesToRefresh = new ArrayList<File>();
    try {
      if (pathsToRemove.size() > 0) {
        context.getProgressIndicator().pushState();
        context.getProgressIndicator().setText("Synchronizing output directory...");
        for (Iterator<String> it = pathsToRemove.iterator(); it.hasNext();) {
          String path = it.next();
          final File file = new File(path);
          final boolean deleted = file.delete();
          if (deleted) {
            cache.remove(path);
            filesToRefresh.add(file);
          }
        }
        context.getProgressIndicator().popState();
      }

      Map<Module, Set<GeneratingCompiler.GenerationItem>> moduleToItemMap =
        buildModuleToGenerationItemMap(toGenerate.toArray(new GeneratingCompiler.GenerationItem[toGenerate.size()]));
      List<Module> modules = new ArrayList<Module>(moduleToItemMap.size());
      for (Iterator<Module> it = moduleToItemMap.keySet().iterator(); it.hasNext();) {
        modules.add(it.next());
      }
      ModuleCompilerUtil.sortModules(myProject, modules);

      for (Iterator<Module> it = modules.iterator(); it.hasNext();) {
        context.getProgressIndicator().pushState();
        try {
          final Module module = it.next();
          final Set<GeneratingCompiler.GenerationItem> items = moduleToItemMap.get(module);
          if (items != null && items.size() > 0) {
            final VirtualFile outputDir = getGenerationOutputDir(compiler, module);
            final GeneratingCompiler.GenerationItem[] successfullyGenerated =
              compiler.generate(context, items.toArray(new GeneratingCompiler.GenerationItem[items.size()]), outputDir);
            context.getProgressIndicator().setText("Updating caches...");
            for (int idx = 0; idx < successfullyGenerated.length; idx++) {
              final GeneratingCompiler.GenerationItem item = successfullyGenerated[idx];
              final String fullOutputPath = itemToOutputPathMap.get(item);
              cache.update(fullOutputPath, item.getValidityState());
              filesToRefresh.add(new File(fullOutputPath));
            }
          }
        }
        finally {
          context.getProgressIndicator().popState();
        }
      }
    }
    finally {
      context.getProgressIndicator().pushState();
      CompilerUtil.refreshIOFiles(filesToRefresh.toArray(new File[filesToRefresh.size()]));
      if (cache.isDirty()) {
        context.getProgressIndicator().setText("Saving caches...");
        cache.save();
      }
      context.getProgressIndicator().popState();
    }
    return toGenerate.size() > 0 || filesToRefresh.size() > 0;
  }

  private boolean compileSources(final CompileContextImpl context, final VfsSnapshot snapshot, final TranslatingCompiler compiler, final boolean forceCompile, final boolean isRebuild) {
    final TranslatingCompilerStateCache cache = getTranslatingCompilerCache(compiler);
    final Set<VirtualFile> toCompile = new HashSet<VirtualFile>();
    final Set<String> toDelete = new HashSet<String>();
    final boolean wereFilesDeleted[] = new boolean[] {false};
    final CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(myProject);

    context.getProgressIndicator().pushState();
    try {

      final Set<String> urlsWithSourceRemoved = new HashSet<String>();

      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          findOutOfDateFiles(compiler, snapshot, forceCompile, cache, toCompile, context);

          if (context.getCompileScope() instanceof TrackDependenciesScope && toCompile.size() > 0) { // should add dependent files
            final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
            final PsiManager psiManager = PsiManager.getInstance(myProject);
            final VirtualFile[] filesToCompile = toCompile.toArray(new VirtualFile[toCompile.size()]);
            Set<String> sourcesWithOutputRemoved = getSourcesWithOutputRemoved(cache);
            for (int i = 0; i < filesToCompile.length; i++) {
              final VirtualFile file = filesToCompile[i];
              if (fileTypeManager.getFileTypeByFile(file) == StdFileTypes.JAVA) {
                final PsiFile psiFile = psiManager.findFile(file);
                if (psiFile != null) {
                  addDependentFiles(psiFile, toCompile, cache, snapshot, sourcesWithOutputRemoved, compiler, context);
                }
              }
            }
          }

          if (!isRebuild) {
            final ProgressIndicator progressIndicator = context.getProgressIndicator();
            progressIndicator.pushState();
            progressIndicator.setText("Searching for files to delete...");

            findFilesToDelete(context.getCompileScope(), snapshot, urlsWithSourceRemoved, cache, toCompile, context, toDelete, compilerConfiguration);

            progressIndicator.popState();
          }

        }
      });

      if (toDelete.size() > 0) {
        try {
          wereFilesDeleted[0] = syncOutputDir(urlsWithSourceRemoved, context, toDelete, cache);
        }
        catch (CacheCorruptedException e) {
          LOG.info(e);
          context.requestRebuildNextTime(e.getMessage());
        }
      }

      if (wereFilesDeleted[0] && toDelete.size() > 0) {
        CompilerUtil.refreshPaths(toDelete.toArray(new String[toDelete.size()]));
      }

      if ((wereFilesDeleted[0] || toCompile.size() > 0) && context.getMessageCount(CompilerMessageCategory.ERROR) == 0) {
        final TranslatingCompiler.ExitStatus exitStatus = compiler.compile(context, toCompile.toArray(new VirtualFile[toCompile.size()]));
        updateInternalCaches(cache, context, exitStatus.getSuccessfullyCompiled(), exitStatus.getFilesToRecompile());
      }
    }
    finally {
      if (cache.isDirty()) {
        context.getProgressIndicator().setText("Saving caches...");
        if (cache.isDirty()) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("--Saving translating cache for compiler " + compiler.getDescription());
          }
          cache.save();
          if (LOG.isDebugEnabled()) {
            LOG.debug("--Done");
          }
        }
      }
      context.getProgressIndicator().popState();
    }
    return toCompile.size() > 0 || wereFilesDeleted[0];
  }

  private Set<String> getSourcesWithOutputRemoved(TranslatingCompilerStateCache cache) {
    //final String[] outputUrls = cache.getOutputUrls();
    final Set<String> set = new HashSet<String>();
    for (Iterator<String> it = cache.getOutputUrlsIterator(); it.hasNext();) {
      String outputUrl = it.next();
      if (!myOutputFilesOnDisk.contains(outputUrl)) {
        set.add(cache.getSourceUrl(outputUrl));
      }
    }
    return set;
  }

  private void findFilesToDelete(final CompileScope scope,
                                 VfsSnapshot snapshot, final Set<String> urlsWithSourceRemoved,
                                 final TranslatingCompilerStateCache cache,
                                 final Set<VirtualFile> toCompile,
                                 final CompileContextImpl context,
                                 final Set<String> toDelete,
                                 final CompilerConfiguration compilerConfiguration) {
    final List<String> toRemove = new ArrayList<String>();
    for (Iterator<String> it = cache.getOutputUrlsIterator(); it.hasNext();) {
      final String outputPath = it.next();
      final String sourceUrl = cache.getSourceUrl(outputPath);
      final VirtualFile sourceFile = snapshot.getFileByUrl(sourceUrl);

      boolean needRecompile = false;
      boolean shouldDelete = false;

      if (myOutputFilesOnDisk.contains(outputPath)) {
        if (sourceFile == null) {
          shouldDelete = scope.belongs(sourceUrl);
        }
        else {
          if (toCompile.contains(sourceFile)) {
            // some crazy users store their resources (which is source file for us) directly in the output dir
            // we should not delete files with are both output and source files
            shouldDelete = !FileUtil.pathsEqual(outputPath, VirtualFileManager.extractPath(sourceUrl));
          }
          else {
            final String currentOutputDir = getModuleOutputDirForFile(context, sourceFile);
            if (currentOutputDir != null) {
              final String className = cache.getClassName(outputPath);
              final boolean pathsEqual = (className == null)? true :
                currentOutputDir.regionMatches(
                  !SystemInfo.isFileSystemCaseSensitive, 0, outputPath, 0, outputPath.length() - className.length() - ".class".length() - 1
                );
              if (pathsEqual) {
                shouldDelete = false;
              }
              else {
                // output for this source has been changed or the output dir was changed, need to recompile to the new output dir
                shouldDelete = true;
                needRecompile = true;
              }
            }
            else {
              shouldDelete = true;
            }
          }
        }
      }
      else {
        // output for this source has been deleted or the output dir was changed, need to recompile
        needRecompile = true;
        shouldDelete = true;  // in case the output dir was changed, should delete from the previous location
      }

      if (shouldDelete) {
        toDelete.add(outputPath);
      }

      if (needRecompile) {
        if (sourceFile != null && scope.belongs(sourceUrl)) {
          if (!compilerConfiguration.isExcludedFromCompilation(sourceFile)) {
            toCompile.add(sourceFile);
            toRemove.add(outputPath);
          }
        }
      }
      if (sourceFile == null) {
        urlsWithSourceRemoved.add(outputPath);
      }
    }
    for (Iterator<String> it = toRemove.iterator(); it.hasNext();) {
      cache.remove(it.next());
    }
  }

  private void updateInternalCaches(final TranslatingCompilerStateCache cache, final CompileContextImpl context, final TranslatingCompiler.OutputItem[] successfullyCompiled, final VirtualFile[] filesToRecompile) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        context.getProgressIndicator().setText("Updating caches...");
        final FileTypeManager typeManager = FileTypeManager.getInstance();
        if (LOG.isDebugEnabled()) {
          LOG.debug("Updating internal caches: successfully compiled " + successfullyCompiled.length + " files; toRecompile: " + filesToRecompile.length + " files");
        }
        for (int idx = 0; idx < successfullyCompiled.length; idx++) {
          final TranslatingCompiler.OutputItem item = successfullyCompiled[idx];
          final String outputPath = item.getOutputPath();
          final VirtualFile sourceFile = item.getSourceFile();
          final String className;
          if (outputPath != null && StdFileTypes.JAVA.equals(typeManager.getFileTypeByFile(sourceFile))) {
            final String outputDir = item.getOutputRootDirectory();

            if (outputDir != null) {
              if (!FileUtil.startsWith(outputPath, outputDir)) {
                LOG.assertTrue(false, outputPath + " does not start with " + outputDir);
              }
              className = MakeUtil.relativeClassPathToQName(outputPath.substring(outputDir.length(), outputPath.length()), '/');
            }
            else {
              // outputDir might be null for package-info.java (package annotation)
              className = null;
            }
          }
          else {
            className = null;
          }
          if (LOG.isDebugEnabled()) {
            LOG.debug("Putting: [outputPath, className, sourceFile] = [" + outputPath + ";" + className + ";" + sourceFile.getPresentableUrl() + "]");
          }
          cache.update(outputPath, className, sourceFile);
        }
        for (int idx = 0; idx < filesToRecompile.length; idx++) {
          cache.markAsModified(filesToRecompile[idx]);
        }
      }
    });
  }

  private boolean syncOutputDir(final Set<String> urlsWithSourceRemoved, final CompileContextImpl context, final Set<String> toDelete,
                                final TranslatingCompilerStateCache cache) throws CacheCorruptedException {

    boolean wereFilesDeleted = false;
    DeleteHelper deleteHelper = new DeleteHelper(myProject);
    int current = 0;
    int total = toDelete.size();
    final DependencyCache dependencyCache = context.getDependencyCache();
    final boolean isTestMode = ApplicationManager.getApplication().isUnitTestMode();

    context.getProgressIndicator().pushState();
    try {
      context.getProgressIndicator().setText("Synchronizing output directory...");
      for (Iterator it = toDelete.iterator(); it.hasNext();) {
        final String outputPath = (String)it.next();
        context.getProgressIndicator().setFraction(((double)(++current)) / total);
        if (deleteHelper.delete(outputPath)) {
          wereFilesDeleted = true;
          String qName = cache.getClassName(outputPath);
          if (qName != null) {
            final int id = dependencyCache.getSymbolTable().getId(qName);
            dependencyCache.addTraverseRoot(id);
            if (urlsWithSourceRemoved.contains(outputPath)) {
              dependencyCache.markSourceRemoved(id);
            }
          }
          if (isTestMode) {
            CompilerManagerImpl.addDeletedPath(outputPath);
          }
          cache.remove(outputPath);
        }
      }
      deleteHelper.finish();
      return wereFilesDeleted;
    }
    finally {
      context.getProgressIndicator().popState();
    }
  }

  private void findOutOfDateFiles(final TranslatingCompiler compiler,
                                  VfsSnapshot snapshot,
                                  final boolean forceCompile,
                                  final TranslatingCompilerStateCache cache,
                                  final Set<VirtualFile> toCompile,
                                  CompileContext context) {

    final CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(myProject);
    final List<VirtualFile> compilableFiles = getCompilableFiles(compiler, snapshot, context);
    for (Iterator<VirtualFile> it = compilableFiles.iterator(); it.hasNext();) {
      final VirtualFile file = it.next();
      if (!forceCompile) {
        if (compilerConfiguration.isExcludedFromCompilation(file)) {
          continue;
        }
      }
      if (forceCompile || file.getTimeStamp() != cache.getSourceTimestamp(snapshot.getUrlByFile(file))) {
        if (LOG.isDebugEnabled()) {
          final String url = snapshot.getUrlByFile(file);
          LOG.debug("File is out-of-date: " + url + "; current timestamp = " + file.getTimeStamp() + "; stored timestamp = " + cache.getSourceTimestamp(url));
        }
        toCompile.add(file);
      }
    }
  }

  private void addDependentFiles(final PsiFile psiFile,
                                 Set<VirtualFile> toCompile,
                                 final TranslatingCompilerStateCache cache,
                                 VfsSnapshot snapshot,
                                 Set<String> sourcesWithOutputRemoved, TranslatingCompiler compiler, CompileContextImpl context) {
    final DependenciesBuilder builder = new ForwardDependenciesBuilder(myProject, new AnalysisScope(psiFile));
    builder.analyze();
    final Map<PsiFile, Set<PsiFile>> dependencies = builder.getDependencies();
    final Set<PsiFile> dependentFiles = dependencies.get(psiFile);
    if (dependentFiles != null && dependentFiles.size() > 0) {
      for (final PsiFile dependentFile : dependentFiles) {
        if (dependentFile instanceof PsiCompiledElement) {
          continue;
        }
        final VirtualFile vFile = dependentFile.getVirtualFile();
        if (toCompile.contains(vFile)) {
          continue;
        }
        String url = snapshot.getUrlByFile(vFile);
        if (url == null) { // the file does not belong to this snapshot
          url = vFile.getUrl();
        }
        if (!sourcesWithOutputRemoved.contains(url)) {
          if (vFile.getTimeStamp() == cache.getSourceTimestamp(url)) {
            continue;
          }
        }
        if (!compiler.isCompilableFile(vFile, context)) {
          continue;
        }
        toCompile.add(vFile);
        addDependentFiles(dependentFile, toCompile, cache, snapshot, sourcesWithOutputRemoved, compiler, context);
      }
    }
  }

  private List<VirtualFile> getCompilableFiles(final TranslatingCompiler compiler, final VfsSnapshot snapshot, final CompileContext context) {
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>(snapshot.size());

    snapshot.forEachUrl(new TObjectProcedure<String>() {
      public boolean execute(final String url) {
        final VirtualFile file = snapshot.getFileByUrl(url);
        if (compiler.isCompilableFile(file, context)) {
          result.add(file);
        }
        return true;
      }
    });

    return result;
  }

  private String getModuleOutputDirForFile(CompileContext context, VirtualFile file) {
    final Module module = context.getModuleByFile(file);
    if (module == null) {
      return null; // looks like file invalidated
    }
    final ProjectFileIndex fileIndex = myProjectRootManager.getFileIndex();
    return getModuleOutputPath(module, fileIndex.isInTestSourceContent(file));
  }


  // [mike] performance optimization - this method is accessed > 15,000 times in Aurora
  private String getModuleOutputPath(final Module module, boolean inTestSourceContent) {
    final Map<Module, String> map = inTestSourceContent? myModuleTestOutputPaths : myModuleOutputPaths;
    String path = map.get(module);
    if (path == null) {
      path = CompilerPaths.getModuleOutputPath(module, inTestSourceContent);
      map.put(module, path);
    }

    return path;
  }

  private boolean processFiles(final FileProcessingCompilerAdapter adapter, final boolean forceCompile) {
    final CompileContext context = adapter.getCompileContext();
    final FileProcessingCompilerStateCache cache = getFileProcessingCompilerCache(adapter.getCompiler());
    final FileProcessingCompiler.ProcessingItem[] items = adapter.getProcessingItems();
    if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
      return false;
    }
    final List<FileProcessingCompiler.ProcessingItem> toProcess = new ArrayList<FileProcessingCompiler.ProcessingItem>();
    final Set<String> allUrls = new HashSet<String>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (int idx = 0; idx < items.length; idx++) {
          FileProcessingCompiler.ProcessingItem item = items[idx];
          final VirtualFile file = item.getFile();
          final String url = file.getUrl();
          allUrls.add(url);
          if (!forceCompile && cache.getTimestamp(url) == file.getTimeStamp()) {
            final ValidityState state = cache.getExtState(url);
            final ValidityState itemState = item.getValidityState();
            if (state != null? state.equalsTo(itemState) : itemState == null) {
              continue;
            }
          }
          toProcess.add(item);
        }
      }
    });

    final String[] urls = cache.getUrls();
    if (urls.length > 0) {
      context.getProgressIndicator().pushState();
      context.getProgressIndicator().setText("Processing outdated files...");
      final CompileScope scope = context.getCompileScope();
      final List<String> urlsToRemove = new ArrayList<String>();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          for (int idx = 0; idx < urls.length; idx++) {
            final String url = urls[idx];
            if (!allUrls.contains(url)) {
              if (scope.belongs(url)) {
                urlsToRemove.add(url);
              }
            }
          }
        }
      });
      if (urlsToRemove.size() > 0) {
        for (Iterator<String> it = urlsToRemove.iterator(); it.hasNext();) {
          final String url = it.next();
          adapter.processOutdatedItem(context, url, cache.getExtState(url));
          cache.remove(url);
        }
      }
      context.getProgressIndicator().popState();
    }

    if (toProcess.size() == 0) {
      return false;
    }

    context.getProgressIndicator().pushState();
    final FileProcessingCompiler.ProcessingItem[] processed =
      adapter.process(toProcess.toArray(new FileProcessingCompiler.ProcessingItem[toProcess.size()]));
    context.getProgressIndicator().popState();

    if (processed.length > 0) {
      context.getProgressIndicator().pushState();
      context.getProgressIndicator().setText("Updating caches...");
      try {
        final VirtualFile[] vFiles = new VirtualFile[processed.length];
        for (int idx = 0; idx < processed.length; idx++) {
          vFiles[idx] = processed[idx].getFile();
        }
        CompilerUtil.refreshVirtualFiles(vFiles);
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            for (int idx = 0; idx < processed.length; idx++) {
              FileProcessingCompiler.ProcessingItem item = processed[idx];
              cache.update(item.getFile(), item.getValidityState());
            }
          }
        });
      }
      finally {
        if (cache.isDirty()) {
          context.getProgressIndicator().setText("Saving caches...");
          cache.save();
        }
        context.getProgressIndicator().popState();
      }
    }
    return true;
  }

  public TranslatingCompilerStateCache getTranslatingCompilerCache(TranslatingCompiler compiler) {
    Object cache = myCompilerToCacheMap.get(compiler);
    if (cache == null) {
      cache = new TranslatingCompilerStateCache(myCachesDirectoryPath, getIdPrefix(compiler));
      myCompilerToCacheMap.put(compiler, cache);
    }
    else {
      LOG.assertTrue(cache instanceof TranslatingCompilerStateCache);
    }
    return (TranslatingCompilerStateCache)cache;
  }

  private FileProcessingCompilerStateCache getFileProcessingCompilerCache(FileProcessingCompiler compiler) {
    Object cache = myCompilerToCacheMap.get(compiler);
    if (cache == null) {
      cache = new FileProcessingCompilerStateCache(myCachesDirectoryPath, getIdPrefix(compiler), compiler);
      myCompilerToCacheMap.put(compiler, cache);
    }
    else {
      LOG.assertTrue(cache instanceof FileProcessingCompilerStateCache);
    }
    return (FileProcessingCompilerStateCache)cache;
  }

  private StateCache<ValidityState> getGeneratingCompilerCache(final GeneratingCompiler compiler) {
    Object cache = myCompilerToCacheMap.get(compiler);
    if (cache == null) {
      cache = new StateCache<ValidityState>(myCachesDirectoryPath + File.separator + getIdPrefix(compiler) + "_timestamp.dat") {
        public ValidityState read(DataInputStream stream) throws IOException {
          return compiler.createValidityState(stream);
        }

        public void write(ValidityState validityState, DataOutputStream stream) throws IOException {
          validityState.save(stream);
        }
      };
      myCompilerToCacheMap.put(compiler, cache);
    }
    return (StateCache<ValidityState>)cache;
  }

  private String getIdPrefix(Compiler compiler) {
    String description = compiler.getDescription();
    return description.toLowerCase().replaceAll("\\s+", "_");
  }

  public void executeCompileTask(final CompileTask task, final CompileScope scope, final String contentName, final Runnable onTaskFinished) {
    final CompilerProgressIndicator indicator = new CompilerProgressIndicator(
      myProject,
      CompilerWorkspaceConfiguration.getInstance(myProject).COMPILE_IN_BACKGROUND,
      contentName
    );
    final CompileContextImpl compileContext = new CompileContextImpl(myProject, indicator, scope, null, this, false);

    FileDocumentManager.getInstance().saveAllDocuments();

    new Thread("Compile Task Thread") {
      public void run() {
        synchronized (CompilerManager.getInstance(myProject)) {
          ProgressManager.getInstance().runProcess(new Runnable() {
            public void run() {
              try {
                task.execute(compileContext);
              }
              catch (ProcessCanceledException ex) {
                // suppressed
              }
              finally {
                if (onTaskFinished != null) {
                  onTaskFinished.run();
                }
              }
            }
          }, compileContext.getProgressIndicator());
        }
      }
    }.start();
  }

  private boolean executeCompileTasks(CompileContext context, boolean beforeTasks) {
    final CompilerManager manager = CompilerManager.getInstance(myProject);
    final ProgressIndicator progressIndicator = context.getProgressIndicator();
    try {
      CompileTask[] tasks = beforeTasks ? (CompileTask[])manager.getBeforeTasks() : manager.getAfterTasks();
      if (tasks.length > 0) {
        progressIndicator.setText(beforeTasks ? "Executing pre-compile tasks..." : "Executing post-compile tasks...");
        for (int idx = 0; idx < tasks.length; idx++) {
          CompileTask task = tasks[idx];
          if (!task.execute(context)) {
            return false;
          }
        }
      }
    }
    finally {
      WindowManager.getInstance().getStatusBar(myProject).setInfo("");
      if (progressIndicator instanceof CompilerProgressIndicator) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            ((CompilerProgressIndicator)progressIndicator).showCompilerContent();
          }
        });
      }
    }
    return true;
  }
  // todo: add validation for module chunks: all modules that form a chunk must have the same JDK
  private boolean validateCompilerConfiguration(final CompileScope scope, boolean checkOutputAndSourceIntersection) {
    final Module[] scopeModules = scope.getAffectedModules()/*ModuleManager.getInstance(myProject).getModules()*/;
    final List<String> modulesWithoutOutputPathSpecified = new ArrayList<String>();
    final List<String> modulesWithoutJdkAssigned = new ArrayList<String>();
    final Set<File> nonExistingOutputPaths = new HashSet<File>();

    for (int idx = 0; idx < scopeModules.length; idx++) {
      final Module module = scopeModules[idx];
      if (ModuleType.J2EE_APPLICATION.equals(module.getModuleType())) {
        continue; // makes no sence to demand jdk & output paths for such modules 
      }
      final boolean hasSources = hasSources(module, false);
      final boolean hasTestSources = hasSources(module, true);
      if (!hasSources && !hasTestSources) {
        // If module contains no sources, shouldn't have to select JDK or output directory (SCR #19333)
        // todo still there may be problems with this approach if some generated files are attributed by this module
        continue;
      }
      final ProjectJdk jdk = ModuleRootManager.getInstance(module).getJdk();
      if (jdk == null) {
        modulesWithoutJdkAssigned.add(module.getName());
      }
      final String outputPath = getModuleOutputPath(module, false);
      final String testsOutputPath = getModuleOutputPath(module, true);
      if (outputPath == null && testsOutputPath == null) {
        modulesWithoutOutputPathSpecified.add(module.getName());
      }
      else {
        if (outputPath != null) {
          final File file = new File(outputPath.replace('/', File.separatorChar));
          if (!file.exists()) {
            nonExistingOutputPaths.add(file);
          }
        }
        else {
          if (hasSources) {
            modulesWithoutOutputPathSpecified.add(module.getName());
          }
        }
        if (testsOutputPath != null) {
          final File f = new File(testsOutputPath.replace('/', File.separatorChar));
          if (!f.exists()) {
            nonExistingOutputPaths.add(f);
          }
        }
        else {
          if (hasTestSources) {
            modulesWithoutOutputPathSpecified.add(module.getName());
          }
        }
      }
    }
    if (modulesWithoutJdkAssigned.size() > 0) {
      showNotSpecifiedError(modulesWithoutJdkAssigned, "the JDK", LibrariesEditor.NAME);
      return false;
    }

    if (modulesWithoutOutputPathSpecified.size() > 0) {
      showNotSpecifiedError(modulesWithoutOutputPathSpecified, "the output path", ContentEntriesEditor.NAME);
      return false;
    }

    if (nonExistingOutputPaths.size() > 0) {
      for (Iterator<File> it = nonExistingOutputPaths.iterator(); it.hasNext();) {
        File file = it.next();
        final boolean succeeded = file.mkdirs();
        if (!succeeded) {
          Messages.showMessageDialog(myProject, "Failed to create directory " + file.getPath(), "Unable To Create Directory", Messages.getErrorIcon());
          return false;
        }
      }
      final Boolean refreshSuccess = ApplicationManager.getApplication().runWriteAction(new Computable<Boolean>() {
          public Boolean compute() {
            for (Iterator<File> it = nonExistingOutputPaths.iterator(); it.hasNext();) {
              File file = it.next();
              final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
              if (vFile == null) {
                return Boolean.FALSE;
              }
            }
            return Boolean.TRUE;
          }
        });
      if (!refreshSuccess.booleanValue()) {
        return false;
      }
    }

    if (checkOutputAndSourceIntersection) {
      if (myShouldClearOutputDirectory) {
        if (!validateOutputAndSourcePathsIntersection()) {
          return false;
        }
      }
    }
    final List<Chunk<Module>> chunks = ModuleCompilerUtil.getSortedModuleChunks(myProject, scopeModules);
    for (Iterator<Chunk<Module>> it = chunks.iterator(); it.hasNext();) {
      final Set<Module> chunkModules = it.next().getNodes();
      if (chunkModules.size() <= 1) {
        continue; // no need to check one-module chunks
      }
      ProjectJdk jdk = null;
      for (Iterator<Module> iterator = chunkModules.iterator(); iterator.hasNext();) {
        final Module module = iterator.next();
        final ProjectJdk moduleJdk = ModuleRootManager.getInstance(module).getJdk();
        if (jdk == null) {
          jdk = moduleJdk;
        }
        else {
          if (!jdk.equals(moduleJdk)) {
            showCyclicModulesHaveDifferentJdksError(chunkModules.iterator());
            return false;
          }
        }
      }
    }
    final Compiler[] allCompilers = CompilerManager.getInstance(myProject).getCompilers(Compiler.class);
    for (int idx = 0; idx < allCompilers.length; idx++) {
      Compiler compiler = allCompilers[idx];
      if (!compiler.validateConfiguration(scope)) {
        return false;
      }
    }
    if (!J2EEModuleUtilEx.checkDependentModulesOutputPathConsistency(myProject, scopeModules, true)) {
      return false;
    }
    return true;
  }

  private void showCyclicModulesHaveDifferentJdksError(Iterator<Module> modulesIterator) {
    String moduleNameToSelect = null;
    final StringBuffer message = new StringBuffer();
    message.append("The following modules must have the same JDK assigned because of cyclic dependencies between them:");
    while (modulesIterator.hasNext()) {
      final Module module = modulesIterator.next();
      if (moduleNameToSelect == null) {
        moduleNameToSelect = module.getName();
      }
      message.append("\n").append("\"").append(module.getName()).append("\"");
    }
    message.append("\nPlease update modules configuration");
    Messages.showMessageDialog(myProject, message.toString(), "Cannot Start Compiler", Messages.getErrorIcon());
    showConfigurationDialog(moduleNameToSelect, null);
  }

  private boolean hasSources(Module module, boolean checkTestSources) {
    final ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
    for (int idx = 0; idx < contentEntries.length; idx++) {
      final ContentEntry contentEntry = contentEntries[idx];
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (int i = 0; i < sourceFolders.length; i++) {
        final SourceFolder sourceFolder = sourceFolders[i];
        if (sourceFolder.getFile() == null) {
          continue; // skip invalid source folders
        }
        if (checkTestSources) {
          if (sourceFolder.isTestSource()) {
            return true;
          }
        }
        else {
          if (!sourceFolder.isTestSource()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private void showNotSpecifiedError(List<String> modules, final String whatNotSpecified, String tabNameToSelect) {
    StringBuffer names = new StringBuffer();
    String nameToSelect = null;
    for (Iterator<String> it = modules.iterator(); it.hasNext();) {
      String name = it.next();
      if (nameToSelect == null) {
        nameToSelect = name;
      }
      if (names.length() > 0) {
        names.append(",\n");
      }
      names.append("\"");
      names.append(name);
      names.append("\"");
    }
    String message = "Cannot start compiler: " + whatNotSpecified + " is not specified for" +
          (modules.size() > 1 ? " modules\n" : " module ") + names.toString() +
          ".\nSpecify " + whatNotSpecified + " in Configure Project.";

    if(ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.assertTrue(false, message);
    }
    
    Messages.showMessageDialog(
      myProject,
      message,
      "Cannot Start Compiler",
      Messages.getErrorIcon()
    );
    showConfigurationDialog(nameToSelect, tabNameToSelect);
  }

  private boolean validateOutputAndSourcePathsIntersection() {
    final Module[] allModules = ModuleManager.getInstance(myProject).getModules();
    final VirtualFile[] outputPaths = CompilerPathsEx.getOutputDirectories(allModules);
    final Set<VirtualFile> affectedOutputPaths = new HashSet<VirtualFile>();
    for (int idx = 0; idx < allModules.length; idx++) {
      final ModuleRootManager rootManager = ModuleRootManager.getInstance(allModules[idx]);
      final VirtualFile[] sourceRoots = rootManager.getSourceRoots();
      for (int j = 0; j < outputPaths.length; j++) {
        VirtualFile outputPath = outputPaths[j];
        for (int i = 0; i < sourceRoots.length; i++) {
          VirtualFile sourceRoot = sourceRoots[i];
          if (VfsUtil.isAncestor(outputPath, sourceRoot, true) || VfsUtil.isAncestor(sourceRoot, outputPath, false)) {
            affectedOutputPaths.add(outputPath);
          }
        }
      }
    }
    if (affectedOutputPaths.size() > 0) {
      final StringBuffer message = new StringBuffer();
      message.append("Compiler option \"Clear output directory on rebuild\" is currently on.\nHowever, source files may exist in the following output path");
      if (affectedOutputPaths.size() > 1) {
        message.append("s:\n");
      }
      else {
        message.append(":\n");
      }
      for (Iterator<VirtualFile> it = affectedOutputPaths.iterator(); it.hasNext();) {
        message.append(it.next().getPath().replace('/', File.separatorChar));
        message.append("\n");
      }
      message.append("\nCompilation will proceed without clearing output directories.");
      final int answer = Messages.showOkCancelDialog(myProject, message.toString(), "Clear Output Files", Messages.getWarningIcon());
      if (answer == 0) { // ok
        myShouldClearOutputDirectory = false;
        return true;
      }
      else {
        return false;
      }
    }
    return true;
  }

  private void showConfigurationDialog(String moduleNameToSelect, String tabNameToSelect) {
    ModulesConfigurator.showDialog(myProject, moduleNameToSelect, tabNameToSelect, false);
  }

  private static class VfsSnapshot {
    private THashMap<String, VirtualFile> myUrlToFile;
    private THashMap<VirtualFile, String> myFileToUrl;

    public VfsSnapshot(final VirtualFile[] files) {
      myUrlToFile = new THashMap<String, VirtualFile>(files.length);
      myFileToUrl = new THashMap<VirtualFile, String>(files.length);
      for (int i = 0; i < files.length; i++) {
        final VirtualFile file = files[i];
        final String url = file.getUrl();
        myUrlToFile.put(url, file);
        myFileToUrl.put(file, url);
      }
    }

    public VirtualFile getFileByUrl(final String url) {
      return myUrlToFile.get(url);
    }

    public String getUrlByFile(final VirtualFile file) {
      return myFileToUrl.get(file);
    }

    public void forEachUrl(TObjectProcedure<String> p) {
      myUrlToFile.forEachKey(p);
    }

    public int size() {
      return myUrlToFile.size();
    }
  }
}
