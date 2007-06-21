package com.intellij.jar;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.PackagingCompiler;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.compiler.make.BuildInstructionVisitor;
import com.intellij.openapi.compiler.make.BuildRecipe;
import com.intellij.openapi.compiler.make.FileCopyInstruction;
import com.intellij.openapi.deployment.PackagingConfiguration;
import com.intellij.openapi.deployment.ModuleLink;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.IOUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectLongHashMap;
import gnu.trove.TObjectLongProcedure;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class JarCompiler implements PackagingCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.jar.JarCompiler");
  private static final JarCompiler INSTANCE = new JarCompiler();

  public static JarCompiler getInstance() {
    return INSTANCE;
  }

  public void processOutdatedItem(final CompileContext context, String url, final ValidityState state) {
    if (state != null) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          MyValState valState = (MyValState)state;
          String jarPath = valState.getOutputJarUrl();
          if (jarPath != null) {
            FileUtil.delete(new File(VfsUtil.urlToPath(jarPath)));
          }
        }
      });
    }
  }

  public ValidityState createValidityState(DataInputStream is) throws IOException {
    return new MyValState(is);
  }

  static class MyValState implements ValidityState {
    private final String myModuleName;
    private final String[] sourceUrls;
    private final long[] timestamps;
    private final String outputJarUrl;
    private final long outputJarTimestamp;

    public MyValState(final Module module) {
      myModuleName = module.getName();
      final BuildJarSettings jarSettings = BuildJarSettings.getInstance(module);
      outputJarUrl = jarSettings.getJarUrl();
      outputJarTimestamp = new File(VfsUtil.urlToPath(outputJarUrl)).lastModified();
      final TObjectLongHashMap<String> url2Timestamps = new TObjectLongHashMap<String>();
      ApplicationManager.getApplication().runReadAction(new Runnable(){
        public void run() {
          BuildRecipe buildRecipe = BuildJarProjectSettings.getBuildRecipe(module, jarSettings);
          buildRecipe.visitInstructions(new BuildInstructionVisitor() {
            public boolean visitFileCopyInstruction(FileCopyInstruction instruction) throws Exception {
              File source = instruction.getFile();
              VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(source.getPath()));
              if (virtualFile != null) {
                addFilesToMap(virtualFile, url2Timestamps);
              }
              return true;
            }
          }, false);
        }
      });
      sourceUrls = new String[url2Timestamps.size()];
      timestamps = new long[url2Timestamps.size()];
      TObjectLongProcedure<String> iterator = new TObjectLongProcedure<String>() {
        int i = 0;
        public boolean execute(final String url, final long timestamp) {
          sourceUrls[i] = url;
          timestamps[i] = timestamp;
          i++;
          return true;
        }
      };
      url2Timestamps.forEachEntry(iterator);
    }

    private static void addFilesToMap(final VirtualFile virtualFile, final TObjectLongHashMap<String> url2Timestamps) {
      if (virtualFile.isDirectory()) {
        VirtualFile[] children = virtualFile.getChildren();
        for (VirtualFile child : children) {
          addFilesToMap(child, url2Timestamps);
        }
      }
      else {
        long timestamp = virtualFile.getModificationStamp();
        url2Timestamps.put(virtualFile.getUrl(), timestamp);
      }
    }

    public MyValState(final DataInputStream is) throws IOException {
      myModuleName = IOUtil.readString(is);
      int size = is.readInt();
      sourceUrls = new String[size];
      timestamps = new long[size];
      for (int i=0;i<size;i++) {
        String url = IOUtil.readString(is);
        long timestamp = is.readLong();
        sourceUrls[i] = url;
        timestamps[i] = timestamp;
      }
      outputJarUrl = IOUtil.readString(is);
      outputJarTimestamp = is.readLong();
    }

    public void save(DataOutputStream os) throws IOException {
      IOUtil.writeString(myModuleName,os);
      int size = sourceUrls.length;
      os.writeInt(size);
      for (int i=0;i<size;i++) {
        String url = sourceUrls[i];
        long timestamp = timestamps[i];
        IOUtil.writeString(url, os);
        os.writeLong(timestamp);
      }
      IOUtil.writeString(outputJarUrl, os);
      os.writeLong(outputJarTimestamp);
    }

    public String getOutputJarUrl() {
      return outputJarUrl;
    }

    public boolean equalsTo(ValidityState otherState) {
      if (!(otherState instanceof MyValState)) return false;
      final MyValState other = (MyValState)otherState;
      if (!myModuleName.equals(other.myModuleName)) return false;
      if (sourceUrls.length != other.sourceUrls.length) return false;
      for (int i = 0; i < sourceUrls.length; i++) {
        String url = sourceUrls[i];
        long timestamp = timestamps[i];
        if (!url.equals(other.sourceUrls[i]) || timestamp != other.timestamps[i]) return false;
      }
      if (!Comparing.strEqual(outputJarUrl,other.outputJarUrl)) return false;
      if (outputJarTimestamp != other.outputJarTimestamp) return false;
      return true;
    }
  }

  static class MyProcItem implements ProcessingItem {
    private final Module myModule;

    public MyProcItem(Module module) {
      myModule = module;
    }

    @NotNull
    public VirtualFile getFile() {
      // return (semifake) file url to store compiler cache entry under
      return myModule.getModuleFile();
    }

    @NotNull
    public MyValState getValidityState() {
      return new MyValState(myModule);
    }

    public Module getModule() {
      return myModule;
    }
  }

  @NotNull
  public ProcessingItem[] getProcessingItems(final CompileContext context) {
    return ApplicationManager.getApplication().runReadAction(new Computable<ProcessingItem[]>(){
      public ProcessingItem[] compute() {
        final CompileScope compileScope = context.getCompileScope();
        final Module[] affectedModules = compileScope.getAffectedModules();
        if (affectedModules.length == 0) return ProcessingItem.EMPTY_ARRAY;
        Project project = affectedModules[0].getProject();
        Module[] modules = ModuleManager.getInstance(project).getModules();
        Collection<Module> modulesToRebuild = new THashSet<Module>();
        for (Module module : modules) {
          BuildJarSettings jarSettings = BuildJarSettings.getInstance(module);
          if (jarSettings == null || !jarSettings.isBuildJar()) continue;
          PackagingConfiguration packagingConfiguration = jarSettings.getPackagingConfiguration();
          ModuleLink[] containingModules = packagingConfiguration.getContainingModules();
          for (ModuleLink moduleLink : containingModules) {
            Module containingModule = moduleLink.getModule();
            if (ArrayUtil.find(affectedModules, containingModule) != -1) {
              modulesToRebuild.add(module);
            }
          }
        }

        ProcessingItem[] result = new ProcessingItem[modulesToRebuild.size()];
        int i=0;
        for (Module moduleToBuild : modulesToRebuild) {
          if (moduleToBuild.getModuleFile() == null) continue;
          result[i++] = new MyProcItem(moduleToBuild);
        }
        return result;
      }
    });
  }

  public ProcessingItem[] process(final CompileContext context, final ProcessingItem[] items) {
    try {
      for (ProcessingItem item : items) {
        MyProcItem procItem = (MyProcItem)item;
        Module module = procItem.getModule();
        BuildJarSettings jarSettings = BuildJarSettings.getInstance(module);
        BuildJarProjectSettings.buildJar(module, jarSettings, context.getProgressIndicator());
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return items;
  }

  @NotNull
  public String getDescription() {
    return "jar compile";
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }
}
