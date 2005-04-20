package com.intellij.compiler;

import com.intellij.compiler.impl.CompileDriver;
import com.intellij.compiler.impl.CompositeScope;
import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.compiler.impl.OneProjectItemCompileScope;
import com.intellij.compiler.impl.javaCompiler.JavaCompiler;
import com.intellij.compiler.impl.resourceCompiler.ResourceCompiler;
import com.intellij.compiler.impl.rmiCompiler.RmicCompiler;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.Compiler;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CompilerManagerImpl extends CompilerManager implements ProjectComponent{
  private final Project myProject;

  private List<Compiler> myCompilers = new ArrayList<Compiler>();
  private List<CompileTask> myBeforeTasks = new ArrayList<CompileTask>();
  private List<CompileTask> myAfterTasks = new ArrayList<CompileTask>();
  private EventDispatcher<CompilationStatusListener> myEventDispatcher = EventDispatcher.create(CompilationStatusListener.class);

  public CompilerManagerImpl(Project project, CompilerConfiguration compilerConfiguration) {
    myProject = project;

    // predefined compilers
    addCompiler(new JavaCompiler(myProject));
    addCompiler(new ResourceCompiler(myProject, compilerConfiguration));
    addCompiler(new RmicCompiler(myProject));
    //
    //addCompiler(new DummyTransformingCompiler()); // this one is for testing purposes only
    //addCompiler(new DummySourceGeneratingCompiler(myProject)); // this one is for testing purposes only
    /*
    if (!ApplicationManagerEx.getApplicationEx().isUnitTestMode()) {
      addCompiler(new AspectInstanceCompiler(myProject));
      addCompiler(new AspectWeaver(myProject));
    }
    */
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public final void addCompiler(Compiler compiler) {
    myCompilers.add(compiler);
  }

  public final void removeCompiler(Compiler compiler) {
    myCompilers.remove(compiler);
  }

  public Compiler[] getCompilers(Class compilerClass) {
    final List<Compiler> compilers = new ArrayList<Compiler>(myCompilers.size());
    for (Iterator<Compiler> it = myCompilers.iterator(); it.hasNext();) {
      final Compiler item = it.next();
      if (compilerClass.isAssignableFrom(item.getClass())) {
        compilers.add(item);
      }
    }
    return (Compiler[])compilers.toArray(new Compiler[compilers.size()]);
  }

  public final void addBeforeTask(CompileTask task) {
    myBeforeTasks.add(task);
  }

  public final void addAfterTask(CompileTask task) {
    myAfterTasks.add(task);
  }

  public CompileTask[] getBeforeTasks() {
    return (CompileTask[])myBeforeTasks.toArray(new CompileTask[myBeforeTasks.size()]);
  }

  public CompileTask[] getAfterTasks() {
    return myAfterTasks.toArray(new CompileTask[myAfterTasks.size()]);
  }

  public void compile(VirtualFile[] files, CompileStatusNotification callback, final boolean trackDependencies) {
    CompileScope[] scopes = new CompileScope[files.length];
    for(int i = 0; i < files.length; i++){
      scopes[i] = new OneProjectItemCompileScope(myProject, files[i]);
    }
    compile(new CompositeScope(scopes), new ListenerNotificator(callback), trackDependencies);
  }

  public void compile(Module module, CompileStatusNotification callback, final boolean trackDependencies) {
    compile(new ModuleCompileScope(module, false), callback, trackDependencies);
  }

  public void compile(CompileScope scope, CompileStatusNotification callback, final boolean trackDependencies) {
    new CompileDriver(myProject).compile(scope, new ListenerNotificator(callback), trackDependencies);
  }

  public void make(CompileStatusNotification callback) {
    new CompileDriver(myProject).make(new ListenerNotificator(callback));
  }

  public void make(Module module, CompileStatusNotification callback) {
    new CompileDriver(myProject).make(module, new ListenerNotificator(callback));
  }

  public void make(Project project, Module[] modules, CompileStatusNotification callback) {
    new CompileDriver(myProject).make(project, modules, new ListenerNotificator(callback));
  }

  public void make(CompileScope scope, CompileStatusNotification callback) {
    new CompileDriver(myProject).make(scope, new ListenerNotificator(callback));
  }

  public void rebuild(CompileStatusNotification callback) {
    new CompileDriver(myProject).rebuild(new ListenerNotificator(callback));
  }

  public void executeTask(CompileTask task, CompileScope scope, String contentName, Runnable onTaskFinished) {
    final CompileDriver compileDriver = new CompileDriver(myProject);
    compileDriver.executeCompileTask(task, scope, contentName, onTaskFinished);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public String getComponentName() {
    return "CompilerManager";
  }

  // Compiler tests support

  private static List<String> ourDeletedPaths;
  private static List<String> ourRecompiledPaths;
  private static List<String> ourCompiledPaths;

  public static void testSetup() {
    ourDeletedPaths = new ArrayList<String>();
    ourRecompiledPaths = new ArrayList<String>();
    ourCompiledPaths = new ArrayList<String>();
  }

  /**
   * @param path a relative to output directory path
   */
  public static void addDeletedPath(String path) {
    ourDeletedPaths.add(path);
  }

  public static void addRecompiledPath(String path) {
    ourRecompiledPaths.add(path);
  }

  public static void addCompiledPath(String path) {
    ourCompiledPaths.add(path);
  }

  public static String[] getPathsToDelete() {
    return (String[])ourDeletedPaths.toArray(new String[ourDeletedPaths.size()]);
  }

  public static String[] getPathsToRecompile() {
    return (String[])ourRecompiledPaths.toArray(new String[ourRecompiledPaths.size()]);
  }

  public static String[] getPathsToCompile() {
    return (String[])ourCompiledPaths.toArray(new String[ourCompiledPaths.size()]);
  }

  public static void clearPathsToCompile() {
    if (ourCompiledPaths != null) {
      ourCompiledPaths.clear();
    }
  }

  public void addCompilationStatusListener(CompilationStatusListener listener) {
    myEventDispatcher.addListener(listener);
  }

  public void removeCompilationStatusListener(CompilationStatusListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  public boolean isExcludedFromCompilation(VirtualFile file) {
    return CompilerConfiguration.getInstance(myProject).isExcludedFromCompilation(file);
  }

  private class ListenerNotificator implements CompileStatusNotification {
    private final CompileStatusNotification myDelegate;

    public ListenerNotificator(CompileStatusNotification delegate) {
      myDelegate = delegate;
    }

    public void finished(boolean aborted, int errors, int warnings) {
      myEventDispatcher.getMulticaster().compilationFinished(aborted, errors, warnings);
      if (myDelegate != null) {
        myDelegate.finished(aborted, errors, warnings);
      }
    }
  }

}
