/*
 * @author: Eugene Zhuravlev
 * Date: Jan 17, 2003
 * Time: 3:22:59 PM
 */
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerException;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.diagnostic.Logger;

public class JavaCompiler implements TranslatingCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.JavaCompiler");
  private Project myProject;
  private BackendCompiler JAVAC_BACKEND;
  private BackendCompiler JIKES_BACKEND;

  public JavaCompiler(Project project) {
    myProject = project;
    JAVAC_BACKEND = new JavacCompiler(project);
    JIKES_BACKEND = new JikesCompiler(project);
  }

  public String getDescription() {
    return "Java Compiler";
  }

  public boolean isCompilableFile(VirtualFile file, CompileContext context) {
    return FileTypeManager.getInstance().getFileTypeByFile(file).equals(StdFileTypes.JAVA);
  }

  public ExitStatus compile(CompileContext context, VirtualFile[] files) {
    final BackendCompiler backEndCompiler = getBackEndCompiler();
    final BackendCompilerWrapper wrapper = new BackendCompilerWrapper(myProject, files, (CompileContextEx)context, backEndCompiler);
    TranslatingCompiler.OutputItem[] outputItems = TranslatingCompiler.EMPTY_OUTPUT_ITEM_ARRAY;
    try {
      outputItems = wrapper.compile();
    }
    catch (CompilerException e) {
      outputItems = TranslatingCompiler.EMPTY_OUTPUT_ITEM_ARRAY;
      context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
    }
    catch (CacheCorruptedException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
      context.requestRebuildNextTime(e.getMessage());
    }

    return new ExitStatusImpl(outputItems, wrapper.getFilesToRecompile());
  }

  public boolean validateConfiguration(CompileScope scope) {
    return getBackEndCompiler().checkCompiler();
  }

  private BackendCompiler getBackEndCompiler() {
    CompilerConfiguration configuration = CompilerConfiguration.getInstance(myProject);
    if (CompilerConfiguration.JIKES.equals(configuration.getDefaultCompiler())) {
      return JIKES_BACKEND;
    }
    else {
      return JAVAC_BACKEND;
    }
  }

  private static class ExitStatusImpl implements ExitStatus {

    private OutputItem[] myOuitputItems;
    private VirtualFile[] myMyFilesToRecompile;

    public ExitStatusImpl(TranslatingCompiler.OutputItem[] ouitputItems, VirtualFile[] myFilesToRecompile) {
      myOuitputItems = ouitputItems;
      myMyFilesToRecompile = myFilesToRecompile;
    }

    public TranslatingCompiler.OutputItem[] getSuccessfullyCompiled() {
      return myOuitputItems;
    }

    public VirtualFile[] getFilesToRecompile() {
      return myMyFilesToRecompile;
    }
  }
}
