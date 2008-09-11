package org.jetbrains.idea.maven.compiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.PropertyResolver;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ResourceFilteringCompiler implements ClassPostProcessingCompiler {
  @NotNull
  public ProcessingItem[] getProcessingItems(CompileContext context) {
    MavenProjectsManager mavenProjectManager = MavenProjectsManager.getInstance(context.getProject());
    if (!mavenProjectManager.isMavenizedProject()) return ProcessingItem.EMPTY_ARRAY;

    CompilerConfiguration config = CompilerConfiguration.getInstance(context.getProject());

    List<ProcessingItem> result = new ArrayList<ProcessingItem>();
    for (Module eachModule : context.getCompileScope().getAffectedModules()) {
      MavenProjectModel mavenProject = mavenProjectManager.findProject(eachModule);
      if (mavenProject == null) continue;

      List<VirtualFile> outputDirs = Arrays.asList(context.getModuleOutputDirectory(eachModule),
                                                   context.getModuleOutputDirectoryForTests(eachModule));

      for (VirtualFile eachSourceRoot : context.getSourceRoots(eachModule)) {
        collectProcessingItems(eachModule,
                               eachSourceRoot,
                               eachSourceRoot,
                               outputDirs,
                               config,
                               mavenProject.isFiltered(eachSourceRoot),
                               result,
                               context.getProgressIndicator());
      }
    }

    return result.toArray(new ProcessingItem[result.size()]);
  }

  private void collectProcessingItems(Module module,
                                      VirtualFile sourceRoot,
                                      VirtualFile currentDir,
                                      List<VirtualFile> outputDirs,
                                      CompilerConfiguration config,
                                      boolean isSourceRootFiltered,
                                      List<ProcessingItem> result,
                                      ProgressIndicator i) {
    i.checkCanceled();

    for (VirtualFile eachSourceFile : currentDir.getChildren()) {
      if (eachSourceFile.isDirectory()) {
        collectProcessingItems(module, sourceRoot, eachSourceFile, outputDirs, config, isSourceRootFiltered, result, i);
      }
      else {
        if (!config.isResourceFile(eachSourceFile.getName())) continue;
        if (eachSourceFile.getFileType().isBinary()) continue;

        String relPath = VfsUtil.getRelativePath(eachSourceFile, sourceRoot, '/');
        for (VirtualFile eachOutputDir : outputDirs) {
          VirtualFile outputFile = eachOutputDir.findFileByRelativePath(relPath);
          if (outputFile != null) {
            result.add(new MyProcessingItem(module, eachSourceFile, outputFile, isSourceRootFiltered));
            break;
          }
        }
      }
    }
  }

  public ProcessingItem[] process(final CompileContext context, ProcessingItem[] items) {
    context.getProgressIndicator().setText("Filtering Maven resources...");
    final List<ProcessingItem> result = new ArrayList<ProcessingItem>(items.length);
    int count = 0;
    for (final ProcessingItem each : items) {
      context.getProgressIndicator().setFraction(((double)count) / items.length);
      context.getProgressIndicator().checkCanceled();

      final MyProcessingItem eachItem = (MyProcessingItem)each;
      final VirtualFile outputFile = each.getFile();
      final VirtualFile sourceFile = eachItem.getSourceFile();

      try {
        if (eachItem.isFiltered()) {
          File file = new File(outputFile.getPath());

          String charset = getCharsetName(sourceFile);
          String text = new String(FileUtil.loadFileBytes(file), charset);

          text = PropertyResolver.resolve(eachItem.getModule(), text);
          FileUtil.writeToFile(file, text.getBytes(charset));
        }
        else {
          boolean wasFiltered = outputFile.getTimeStamp() != sourceFile.getTimeStamp();
          if (wasFiltered) {
            FileUtil.copy(new File(sourceFile.getPath()), new File(outputFile.getPath()));
          }
        }
        result.add(each);
      }
      catch (IOException e) {
        context.addMessage(CompilerMessageCategory.ERROR, "Maven: Cannot filter properties", outputFile.getUrl(), -1, -1);
      }
    }
    return result.toArray(new ProcessingItem[result.size()]);
  }

  private String getCharsetName(VirtualFile sourceFile) {
    EncodingManager manager = EncodingManager.getInstance();
    Charset charset;
    if (StdFileTypes.PROPERTIES.equals(sourceFile.getFileType())) {
      charset = manager.getDefaultCharsetForPropertiesFiles(sourceFile);
    } else {
      charset = manager.getEncoding(sourceFile, true);
    }
    if (charset == null) charset = manager.getDefaultCharset();
    return charset.name();
  }

  @NotNull
  public String getDescription() {
    return "Maven Resources Filtering Compiler";
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  public ValidityState createValidityState(DataInput in) throws IOException {
    return MyValididtyState.load(in);
  }

  private static class MyProcessingItem implements ProcessingItem {
    private final Module myModule;
    private final VirtualFile mySourceFile;
    private final VirtualFile myOutputFile;
    private final boolean myFiltered;
    private final MyValididtyState myState;

    public MyProcessingItem(Module module, VirtualFile sourceFile, VirtualFile outputFile, boolean isFiltered) {
      myModule = module;
      mySourceFile = sourceFile;
      myOutputFile = outputFile;
      myFiltered = isFiltered;
      myState = new MyValididtyState(outputFile, isFiltered);
    }

    public VirtualFile getSourceFile() {
      return mySourceFile;
    }

    @NotNull
    public VirtualFile getFile() {
      return myOutputFile;
    }

    public Module getModule() {
      return myModule;
    }

    public boolean isFiltered() {
      return myFiltered;
    }

    public ValidityState getValidityState() {
      return myState;
    }
  }

  private static class MyValididtyState implements ValidityState {
    TimestampValidityState myTimestampState;
    private boolean myFiltered;

    public static MyValididtyState load(DataInput in) throws IOException {
      return new MyValididtyState(TimestampValidityState.load(in), in.readBoolean());
    }

    public MyValididtyState(VirtualFile file, boolean isFiltered) {
      this(new TimestampValidityState(file.getTimeStamp()), isFiltered);
    }

    private MyValididtyState(TimestampValidityState timestampState, boolean isFiltered) {
      myTimestampState = timestampState;
      myFiltered = isFiltered;
    }

    public boolean equalsTo(ValidityState otherState) {
      if (!(otherState instanceof MyValididtyState)) return false;
      MyValididtyState state = (MyValididtyState)otherState;
      return myTimestampState.equalsTo(state.myTimestampState) && myFiltered == state.myFiltered;
    }

    public void save(DataOutput out) throws IOException {
      myTimestampState.save(out);
      out.writeBoolean(myFiltered);
    }
  }
}
