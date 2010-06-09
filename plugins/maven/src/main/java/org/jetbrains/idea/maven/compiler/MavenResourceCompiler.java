/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.compiler;

import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.CompilerIOUtil;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.model.MavenResource;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

public class MavenResourceCompiler implements ClassPostProcessingCompiler {
  private static final Key<List<String>> FILES_TO_DELETE_KEY = Key.create(MavenResourceCompiler.class.getSimpleName() + ".FILES_TO_DELETE");

  private Map<String, Set<String>> myOutputItemsCache = new THashMap<String, Set<String>>();

  public MavenResourceCompiler(Project project) {
    loadCache(project);
  }

  private void loadCache(final Project project) {
    File file = getCacheFile(project);
    if (!file.exists()) return;

    try {
      DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
      try {
        if (in.readInt() != CompilerConfigurationImpl.DEPENDENCY_FORMAT_VERSION) return;
        int modulesSize = in.readInt();
        Map<String, Set<String>> temp = new THashMap<String, Set<String>>();
        while (modulesSize-- > 0) {
          String module = CompilerIOUtil.readString(in);
          int pathsSize = in.readInt();
          Set<String> paths = createPathsSet(pathsSize);
          while (pathsSize-- > 0) {
            paths.add(CompilerIOUtil.readString(in));
          }
          temp.put(module, paths);
        }
        myOutputItemsCache = temp;
      }
      finally {
        in.close();
      }
    }
    catch (IOException e) {
      MavenLog.LOG.warn(e);
    }
  }

  private static Set<String> createPathsSet(int size) {
    return SystemInfo.isFileSystemCaseSensitive
           ? new THashSet<String>(size)
           : new THashSet<String>(size, CaseInsensitiveStringHashingStrategy.INSTANCE);
  }

  private void saveCache(final Project project) {
    File file = getCacheFile(project);
    file.getParentFile().mkdirs();
    try {
      DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
      try {
        out.writeInt(CompilerConfigurationImpl.DEPENDENCY_FORMAT_VERSION);
        out.writeInt(myOutputItemsCache.size());
        for (Map.Entry<String, Set<String>> eachEntry : myOutputItemsCache.entrySet()) {
          String module = eachEntry.getKey();
          Set<String> paths = eachEntry.getValue();

          CompilerIOUtil.writeString(module, out);
          out.writeInt(paths.size());
          for (String eachPath : paths) {
            CompilerIOUtil.writeString(eachPath, out);
          }
        }
      }
      finally {
        out.close();
      }
    }
    catch (IOException e) {
      MavenLog.LOG.error(e);
    }
  }

  private static File getCacheFile(final Project project) {
    return new File(CompilerPaths.getCompilerSystemDirectory(project), "maven_compiler_caches.dat");
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  @NotNull
  public ProcessingItem[] getProcessingItems(final CompileContext context) {
    final Project project = context.getProject();
    final MavenProjectsManager mavenProjectManager = MavenProjectsManager.getInstance(project);
    if (!mavenProjectManager.isMavenizedProject()) return ProcessingItem.EMPTY_ARRAY;
    return new ReadAction<ProcessingItem[]>() {
      protected void run(Result<ProcessingItem[]> resultObject) throws Throwable {
        List<ProcessingItem> allItemsToProcess = new ArrayList<ProcessingItem>();
        List<String> filesToDelete = new ArrayList<String>();

        for (Module eachModule : context.getCompileScope().getAffectedModules()) {
          MavenProject mavenProject = mavenProjectManager.findProject(eachModule);
          if (mavenProject == null) continue;

          Properties properties = loadPropertiesAndFilters(context, mavenProject);

          List<String> nonFilteredExtensions = collectNonFilteredExtensions(mavenProject);
          String escapeString = MavenJDOMUtil.findChildValueByPath(mavenProject.getPluginConfiguration("org.apache.maven.plugins",
                                                                                                       "maven-resources-plugin"),
                                                                   "escapeString", "\\");

          long propertiesHashCode = calculateHashCode(mavenProject, properties);

          List<ProcessingItem> moduleItemsToProcess = new ArrayList<ProcessingItem>();
          collectProcessingItems(eachModule, mavenProject, context, properties, propertiesHashCode,
                                 nonFilteredExtensions, escapeString, false, moduleItemsToProcess);
          collectProcessingItems(eachModule, mavenProject, context, properties, propertiesHashCode,
                                 nonFilteredExtensions, escapeString, true, moduleItemsToProcess);
          collectItemsToDelete(eachModule, moduleItemsToProcess, filesToDelete);
          allItemsToProcess.addAll(moduleItemsToProcess);
        }

        if (!filesToDelete.isEmpty()) {
          allItemsToProcess.add(new FakeProcessingItem());
        }
        context.putUserData(FILES_TO_DELETE_KEY, filesToDelete);
        resultObject.setResult(allItemsToProcess.toArray(new ProcessingItem[allItemsToProcess.size()]));
        removeObsoleteModulesFromCache(project);
        saveCache(project);
      }
    }.execute().getResultObject();
  }

  private static List<String> collectNonFilteredExtensions(MavenProject mavenProject) {
    List<String> result = new ArrayList<String>(Arrays.asList("jpg", "jpeg", "gif", "bmp", "png"));
    Element config = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-resources-plugin");
    if (config == null) return result;

    for (String each : MavenJDOMUtil.findChildrenValuesByPath(config, "nonFilteredFileExtensions", "nonFilteredFileExtension")) {
      result.add(each);
    }
    return result;
  }

  private static long calculateHashCode(MavenProject project, Properties properties) {
    Set<String> sorted = new TreeSet<String>();
    for (Map.Entry<Object, Object> each : properties.entrySet()) {
      sorted.add(each.getKey().toString() + "->" + each.getValue().toString());
    }
    return project.getLastReadStamp() + 31 * sorted.hashCode();
  }

  private static Properties loadPropertiesAndFilters(CompileContext context, MavenProject mavenProject) {
    Properties properties = new Properties();
    properties.putAll(mavenProject.getProperties());

    for (String each : mavenProject.getFilters()) {
      try {
        FileInputStream in = new FileInputStream(each);
        try {
          properties.load(in);
        }
        finally {
          in.close();
        }
      }
      catch (IOException e) {
        String url = VfsUtil.pathToUrl(mavenProject.getFile().getPath());
        context.addMessage(CompilerMessageCategory.WARNING, "Maven: Cannot read the filter. " + e.getMessage(), url, -1, -1);
      }
    }
    return properties;
  }

  private void collectProcessingItems(Module module,
                                      MavenProject mavenProject,
                                      CompileContext context,
                                      Properties properties,
                                      long propertiesHashCode,
                                      List<String> nonFilteredExtensions,
                                      String escapeString,
                                      boolean tests,
                                      List<ProcessingItem> result) {
    String outputDir = CompilerPaths.getModuleOutputPath(module, tests);
    if (outputDir == null) {
      context.addMessage(CompilerMessageCategory.ERROR, "Maven: Module '" + module.getName() + "'output is not specified", null, -1, -1);
      return;
    }

    List<MavenResource> resources = tests ? mavenProject.getTestResources() : mavenProject.getResources();

    for (MavenResource each : resources) {
      VirtualFile dir = LocalFileSystem.getInstance().findFileByPath(each.getDirectory());
      if (dir == null) continue;

      List<Pattern> includes = collectPatterns(each.getIncludes(), "**/*");
      List<Pattern> excludes = collectPatterns(each.getExcludes(), null);
      String targetPath = each.getTargetPath();
      String resourceOutputDir = StringUtil.isEmptyOrSpaces(targetPath)
                                 ? outputDir
                                 : (FileUtil.isAbsolute(targetPath) ? targetPath : outputDir + "/" + targetPath);

      collectProcessingItems(module,
                             dir,
                             dir,
                             resourceOutputDir,
                             includes,
                             excludes,
                             each.isFiltered(),
                             properties,
                             propertiesHashCode,
                             nonFilteredExtensions,
                             escapeString,
                             result,
                             context.getProgressIndicator());
    }
  }

  private List<Pattern> collectPatterns(List<String> values, String defaultValue) {
    List<Pattern> result = new ArrayList<Pattern>();
    if (values == null || values.isEmpty()) {
      if (defaultValue == null) return Collections.emptyList();
      return MavenUtil.collectPattern(defaultValue, result);
    }
    for (String each : values) {
      MavenUtil.collectPattern(each, result);
    }
    return result;
  }

  private void collectProcessingItems(Module module,
                                      VirtualFile sourceRoot,
                                      VirtualFile currentDir,
                                      String outputDir,
                                      List<Pattern> includes,
                                      List<Pattern> excludes,
                                      boolean isSourceRootFiltered,
                                      Properties properties,
                                      long propertiesHashCode,
                                      List<String> nonFilteredExtensions,
                                      String escapeString,
                                      List<ProcessingItem> result,
                                      ProgressIndicator indicator) {
    indicator.checkCanceled();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(module.getProject()).getFileIndex();
    for (VirtualFile eachSourceFile : currentDir.getChildren()) {
      if (eachSourceFile.isDirectory()) {
        collectProcessingItems(module,
                               sourceRoot,
                               eachSourceFile,
                               outputDir,
                               includes,
                               excludes,
                               isSourceRootFiltered,
                               properties,
                               propertiesHashCode,
                               nonFilteredExtensions,
                               escapeString,
                               result,
                               indicator);
      }
      else {
        String relPath = VfsUtil.getRelativePath(eachSourceFile, sourceRoot, '/');
        if (fileIndex.isIgnored(eachSourceFile)) continue;
        if (!MavenUtil.isIncluded(relPath, includes, excludes)) continue;

        String outputPath = outputDir + "/" + relPath;
        long outputFileTimestamp = -1;
        File outputFile = new File(outputPath);
        if (outputFile.exists()) {
          outputFileTimestamp = outputFile.lastModified();
        }
        boolean isFileterd = isSourceRootFiltered && !nonFilteredExtensions.contains(eachSourceFile.getExtension());
        result.add(new MyProcessingItem(module,
                                        eachSourceFile,
                                        outputPath,
                                        outputFileTimestamp,
                                        isFileterd,
                                        properties,
                                        propertiesHashCode,
                                        escapeString));
      }
    }
  }

  private void collectItemsToDelete(Module module, List<ProcessingItem> processingItems, List<String> result) {
    Set<String> currentPaths = createPathsSet(processingItems.size());
    for (ProcessingItem each : processingItems) {
      if (!(each instanceof MyProcessingItem)) continue;
      currentPaths.add(((MyProcessingItem)each).getOutputPath());
    }

    Set<String> cachedPaths = null;
    Set<String> otherModulesCachedPaths = new THashSet<String>();
    for (Map.Entry<String, Set<String>> eachEntry : myOutputItemsCache.entrySet()) {
      if (eachEntry.getKey().equals(module.getName())) {
        cachedPaths = eachEntry.getValue();
      }
      else {
        otherModulesCachedPaths.addAll(eachEntry.getValue());
      }
    }
    myOutputItemsCache.put(module.getName(), currentPaths);
    if (cachedPaths == null) return;

    cachedPaths.removeAll(currentPaths);
    cachedPaths.removeAll(otherModulesCachedPaths);
    result.addAll(cachedPaths);
  }

  private void removeObsoleteModulesFromCache(final Project project) {
    Set<String> existingModules = new THashSet<String>();
    for (Module each : ModuleManager.getInstance(project).getModules()) {
      existingModules.add(each.getName());
    }

    for (String each : new THashSet<String>(myOutputItemsCache.keySet())) {
      if (!existingModules.contains(each)) {
        myOutputItemsCache.remove(each);
      }
    }
  }

  public ProcessingItem[] process(final CompileContext context, ProcessingItem[] items) {
    context.getProgressIndicator().setText("Processing Maven resources...");

    List<ProcessingItem> result = new ArrayList<ProcessingItem>(items.length);
    List<File> filesToRefresh = new ArrayList<File>(items.length);

    deleteOutdatedFile(context.getUserData(FILES_TO_DELETE_KEY), filesToRefresh);

    int count = 0;
    for (final ProcessingItem each : items) {
      if (!(each instanceof MyProcessingItem)) continue;

      context.getProgressIndicator().setFraction(((double)count) / items.length);
      context.getProgressIndicator().checkCanceled();

      MyProcessingItem eachItem = (MyProcessingItem)each;
      VirtualFile sourceVirtualFile = eachItem.getFile();
      File sourceFile = new File(sourceVirtualFile.getPath());
      File outputFile = new File(eachItem.getOutputPath());

      try {
        outputFile.getParentFile().mkdirs();

        boolean shouldFilter = eachItem.isFiltered();
        if (sourceFile.length() > 10 * 1024 * 1024) {
          context.addMessage(CompilerMessageCategory.WARNING,
                             "Maven: File is too big to be filtered. Most likely it is a binary file and should be excluded from filtering.",
                             sourceVirtualFile.getUrl(), -1, -1);
          shouldFilter = false;
        }

        if (shouldFilter) {
          String charset = sourceVirtualFile.getCharset().name();
          String text = new String(FileUtil.loadFileBytes(sourceFile), charset);
          String escapedCharacters = "properties".equals(sourceVirtualFile.getExtension()) ? "\\" : null;
          text = MavenPropertyResolver.resolve(eachItem.getModule(),
                                               text,
                                               eachItem.getProperties(),
                                               eachItem.getEscapeString(),
                                               escapedCharacters);
          FileUtil.writeToFile(outputFile, text.getBytes(charset));
        }
        else {
          FileUtil.copy(sourceFile, outputFile);
        }

        ((MyValididtyState)each.getValidityState()).setOutputFileTimestamp(outputFile.lastModified());
        result.add(each);
        filesToRefresh.add(outputFile);
      }
      catch (IOException e) {
        context.addMessage(CompilerMessageCategory.ERROR,
                           "Maven: Cannot process resource file: " + e.getMessage(),
                           sourceVirtualFile.getUrl(),
                           -1,
                           -1);
      }
    }
    CompilerUtil.refreshIOFiles(filesToRefresh);
    return result.toArray(new ProcessingItem[result.size()]);
  }

  private void deleteOutdatedFile(List<String> filesToDelete, List<File> filesToRefresh) {
    for (String each : filesToDelete) {
      File file = new File(each);
      if (FileUtil.delete(file)) {
        filesToRefresh.add(file);
      }
    }
  }

  @NotNull
  public String getDescription() {
    return "Maven Resource Compiler";
  }

  public ValidityState createValidityState(DataInput in) throws IOException {
    return MyValididtyState.load(in);
  }

  private static class MyProcessingItem implements ProcessingItem {
    private final Module myModule;
    private final VirtualFile mySourceFile;
    private final String myOutputPath;
    private final boolean myFiltered;
    private final Properties myProperties;
    private final String myEscapeString;
    private final MyValididtyState myState;

    public MyProcessingItem(Module module,
                            VirtualFile sourceFile,
                            String outputPath,
                            long outputFileTimestamp,
                            boolean isFiltered,
                            Properties properties,
                            long propertiesHashCode,
                            String escapeString) {
      myModule = module;
      mySourceFile = sourceFile;
      myOutputPath = outputPath;
      myFiltered = isFiltered;
      myProperties = properties;
      myEscapeString = escapeString;
      myState = new MyValididtyState(sourceFile.getTimeStamp(), outputFileTimestamp, isFiltered, propertiesHashCode, escapeString);
    }

    @NotNull
    public VirtualFile getFile() {
      return mySourceFile;
    }

    public String getOutputPath() {
      return myOutputPath;
    }

    public Module getModule() {
      return myModule;
    }

    public boolean isFiltered() {
      return myFiltered;
    }

    public Properties getProperties() {
      return myProperties;
    }

    public String getEscapeString() {
      return myEscapeString;
    }

    public ValidityState getValidityState() {
      return myState;
    }
  }

  private static class FakeProcessingItem implements ProcessingItem {
    private final LightVirtualFile myFile;

    private FakeProcessingItem() {
      myFile = new LightVirtualFile(this.getClass().getName());
    }

    @NotNull
    public VirtualFile getFile() {
      return myFile;
    }

    public ValidityState getValidityState() {
      return null;
    }
  }

  private static class MyValididtyState implements ValidityState {
    private final long mySourceFileTimestamp;
    private volatile long myOutputFileTimestamp;
    private final boolean myFiltered;
    private final long myPropertiesHashCode;
    private final String myEscapeString;

    public static MyValididtyState load(DataInput in) throws IOException {
      return new MyValididtyState(in.readLong(), in.readLong(), in.readBoolean(), in.readLong(), in.readUTF());
    }

    public void setOutputFileTimestamp(long outputFileTimestamp) {
      myOutputFileTimestamp = outputFileTimestamp;
    }

    private MyValididtyState(long sourceFileTimestamp,
                             long outputFileTimestamp,
                             boolean isFiltered,
                             long propertiesHashCode,
                             String escapeString) {
      mySourceFileTimestamp = sourceFileTimestamp;
      myOutputFileTimestamp = outputFileTimestamp;
      myFiltered = isFiltered;
      myPropertiesHashCode = propertiesHashCode;
      myEscapeString = escapeString;
    }

    @Override
    public String toString() {
      return mySourceFileTimestamp + " " + myOutputFileTimestamp + " " + myFiltered + " " + myPropertiesHashCode + " " + myEscapeString;
    }

    public boolean equalsTo(ValidityState otherState) {
      if (!(otherState instanceof MyValididtyState)) return false;
      MyValididtyState that = (MyValididtyState)otherState;

      return mySourceFileTimestamp == that.mySourceFileTimestamp
             && myOutputFileTimestamp == that.myOutputFileTimestamp
             && myFiltered == that.myFiltered
             && myPropertiesHashCode == that.myPropertiesHashCode
             && Comparing.strEqual(myEscapeString, that.myEscapeString);
    }

    public void save(DataOutput out) throws IOException {
      out.writeLong(mySourceFileTimestamp);
      out.writeLong(myOutputFileTimestamp);
      out.writeBoolean(myFiltered);
      out.writeLong(myPropertiesHashCode);
      out.writeUTF(myEscapeString);
    }
  }
}
