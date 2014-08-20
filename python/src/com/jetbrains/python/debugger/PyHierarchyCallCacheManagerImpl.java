/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.debugger;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * HIERARCHY_CALLERS_DATA file attribute contains for every function, defined in file, information about its callers and lines
 * where this function is called by its callers
 *
 * HIERARCHY_CALLEES_DATA file attribute contains for every function, defined in file, information about its callees and lines
 * where this function calls its callees
 *
 */
public class PyHierarchyCallCacheManagerImpl extends PyHierarchyCallCacheManager {
  protected static final Logger LOG = Logger.getInstance(PyHierarchyCallCacheManagerImpl.class.getName());

  public static final FileAttribute HIERARCHY_CALLERS_DATA = new FileAttribute("callers.hierarchy.attribute", 1, true);
  public static final FileAttribute HIERARCHY_CALLEES_DATA = new FileAttribute("callees.hierarchy.attribute", 1, true);

  private final Project myProject;

  public PyHierarchyCallCacheManagerImpl(Project project) {
    myProject = project;
  }

  private final LoadingCache<VirtualFile, String> myHierarchyCallersCache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterAccess(10, TimeUnit.MINUTES)
    .build(
      new CacheLoader<VirtualFile, String>() {
        @Override
        public String load(VirtualFile key) throws Exception {
          return readAttributeFromFile(key, HIERARCHY_CALLERS_DATA);
        }
      }
    );

  private final LoadingCache<VirtualFile, String> myHierarchyCalleesCache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterAccess(10, TimeUnit.MINUTES)
    .build(
      new CacheLoader<VirtualFile, String>() {
        @Override
        public String load(VirtualFile key) throws Exception {
          return readAttributeFromFile(key, HIERARCHY_CALLEES_DATA);
        }
      }
    );

  private static String readAttributeFromFile(VirtualFile key, FileAttribute fileAttribute) {
    byte[] data;
    try {
      data = fileAttribute.readAttributeBytes(key);
    }
    catch (Exception e) {
      data = null;
    }

    String content;
    if (data != null && data.length > 0) {
      content = new String(data);
    }
    else {
      content = null;
    }

    return content != null ? content : "";
  }

  private String readCallerAttribute(VirtualFile file) {
    return readAttribute(myHierarchyCallersCache, file);
  }

  private String readCalleeAttribute(VirtualFile file) {
    return readAttribute(myHierarchyCalleesCache, file);
  }

  private String readAttribute(LoadingCache<VirtualFile, String> cache, VirtualFile file) {
    try {
      String attrContent = cache.get(file);
      if (!StringUtil.isEmpty(attrContent)) {
        return attrContent;
      }
    }
    catch (ExecutionException e) {
    }

    return null;
  }

  private void writeCallerAttribute(VirtualFile file, String attrString) {
    writeAttribute(myHierarchyCallersCache, file, HIERARCHY_CALLERS_DATA, attrString);
  }

  private void writeCalleeAttribute(VirtualFile file, String attrString) {
    writeAttribute(myHierarchyCalleesCache, file, HIERARCHY_CALLEES_DATA, attrString);
  }

  private void writeAttribute(LoadingCache<VirtualFile, String> cache, VirtualFile file, FileAttribute fileAttribute, String attrString) {
    String cachedValue = cache.asMap().get(file);
    if (!attrString.equals(cachedValue)) {
      cache.put(file, attrString);
      writeAttributeToFile(file, fileAttribute, attrString);
    }
  }

  private static void writeAttributeToFile(VirtualFile file, FileAttribute fileAttribute, String attrString) {
    try {
      fileAttribute.writeAttributeBytes(file, attrString.getBytes());
    }
    catch (IOException e) {
      LOG.warn("Can't write attribute " + file.getCanonicalPath() + " " + attrString);
    }
  }

  @Override
  public void recordHierarchyCallInfo(@NotNull PyHierarchyCallInfo callInfo) {
    GlobalSearchScope scope = ProjectScope.getProjectScope(myProject);

    VirtualFile calleeFile = getCalleeFile(callInfo);
    if (calleeFile != null && scope.contains(calleeFile)) {
      recordHierarchyCallerData(calleeFile, callInfo);
    }

    VirtualFile callerFile = getCallerFile(callInfo);
    if (callerFile != null && scope.contains(callerFile)) {
      recordHierarchyCalleeData(callerFile, callInfo);
    }
  }

  private void recordHierarchyCallerData(VirtualFile calleeFile, PyHierarchyCallInfo callInfo) {
    String data = readAttribute(myHierarchyCallersCache, calleeFile);

    //if (callInfo.getCalleeName().endsWith("func2") && callInfo.getCallerName().endsWith("func1") && callInfo.getCallerLine() == 15) {
    //  int i = 1;
    //}

    String[] lines;
    if (data != null) {
      lines = data.split("\n");
    }
    else {
      lines = ArrayUtil.EMPTY_STRING_ARRAY;
    }

    String calleeName = callInfo.getCalleeName();
    String callerName = callInfo.getCallerName();
    String callerFile = callInfo.getCallerFile();
    boolean found = false;
    int i = 0;
    for (String calls: lines) {
      String[] parts = calls.split("\t");
      if (parts.length > 2 && parts[0].equals(calleeName) && parts[1].equals(callerFile) && parts[2].equals(callerName)) {
        found = true;
        lines[i] = PyHierarchyCallDataConverter.hierarchyCallerDataToString(
          PyHierarchyCallDataConverter.stringToHierarchyCallerData(calleeFile.getCanonicalPath(), lines[i])
            .addAllCallerLines(callInfo.toPyHierarchyCallerData()));
      }
      i++;
    }
    if (!found) {
      String[] lines2 = new String[lines.length + 1];
      System.arraycopy(lines, 0, lines2, 0, lines.length);

      lines2[lines2.length - 1] = PyHierarchyCallDataConverter.hierarchyCallerDataToString(callInfo.toPyHierarchyCallerData());
      lines = lines2;
    }
    String attrString = StringUtil.join(lines, "\n");
    writeCallerAttribute(calleeFile, attrString);
  }

  private void recordHierarchyCalleeData(VirtualFile callerFile, PyHierarchyCallInfo callInfo) {
    String data = readAttribute(myHierarchyCalleesCache, callerFile);

    String[] lines;
    if (data != null) {
      lines = data.split("\n");
    }
    else {
      lines = ArrayUtil.EMPTY_STRING_ARRAY;
    }

    String callerName = callInfo.getCallerName();
    String calleeName = callInfo.getCalleeName();
    String calleeFile = callInfo.getCalleeFile();
    boolean found = false;
    int i = 0;
    for (String calls: lines) {
      String[] parts = calls.split("\t");
      if (parts.length > 2 && parts[0].equals(callerName) && parts[1].equals(calleeFile) && parts[2].equals(calleeName)) {
        found = true;
        lines[i] = PyHierarchyCallDataConverter.hierarchyCalleeDataToString(
          PyHierarchyCallDataConverter.stringToHierarchyCalleeData(callerFile.getCanonicalPath(), lines[i])
            .addAllCalleeLines(callInfo.toPyHierarchyCalleeData()));
      }
      i++;
    }
    if (!found) {
      String[] lines2 = new String[lines.length + 1];
      System.arraycopy(lines, 0, lines2, 0, lines.length);

      lines2[lines2.length - 1] = PyHierarchyCallDataConverter.hierarchyCalleeDataToString(callInfo.toPyHierarchyCalleeData());
      lines = lines2;
    }
    String attrString = StringUtil.join(lines, "\n");
    writeCalleeAttribute(callerFile, attrString);
  }

  @Override
  public Object[] findFunctionCallers(@NotNull PyFunction function) {
    VirtualFile calleeFile = getFile(function);
    if (calleeFile != null) {
      return readCallersForFunction(calleeFile, function.getName());
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public Object[] findFunctionCallees(@NotNull PyFunction function) {
    VirtualFile callerFile = getFile(function);
    if (callerFile != null) {
      return readCalleesForFunction(callerFile, function.getName());
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private Object[] readCallersForFunction(VirtualFile calleeFile, String calleeName) {
    String content = readAttribute(myHierarchyCallersCache, calleeFile);
    if (content == null) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    List<PyHierarchyCallerData> callers = Lists.newArrayList();
    String[] lines = content.split("\n");
    for (String callsInfo: lines) {
      String[] parts = callsInfo.split("\t");
      if (calleeName.equals(parts[0])) {
        callers.add(PyHierarchyCallDataConverter.stringToHierarchyCallerData(calleeFile.getCanonicalPath(), callsInfo));
      }
    }
    return ArrayUtil.toObjectArray(callers);
  }

  private Object[] readCalleesForFunction(VirtualFile callerFile, String callerName) {
    String content = readAttribute(myHierarchyCalleesCache, callerFile);
    if (content == null) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    List<PyHierarchyCalleeData> callees = Lists.newArrayList();
    String[] lines = content.split("\n");
    for (String callsInfo: lines) {
      String[] parts = callsInfo.split("\t");
      if (callerName.equals(parts[0])) {
        callees.add(PyHierarchyCallDataConverter.stringToHierarchyCalleeData(callerFile.getCanonicalPath(), callsInfo));
      }
    }
    return ArrayUtil.toObjectArray(callees);
  }

  public static VirtualFile getFile(@NotNull PyFunction function) {
    PsiFile file = function.getContainingFile();

    return file != null ? file.getOriginalFile().getVirtualFile() : null;
  }

  @Override
  public void clearCache() {
    final Ref<Boolean> deleted = Ref.create(false);
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        ProjectFileIndex.SERVICE.getInstance(myProject).iterateContent(new ContentIterator() {
          @Override
          public boolean processFile(VirtualFile fileOrDir) {
            if (readCallerAttribute(fileOrDir) != null
                || readCalleeAttribute(fileOrDir) != null) {
              writeCallerAttribute(fileOrDir, "");
              writeCalleeAttribute(fileOrDir, "");
              deleted.set(true);
            }
            if (ProgressManager.getInstance().getProgressIndicator().isCanceled()) {
              return false;
            }
            return true;
          }
        });
      }
    }, "Cleaning the cache of dynamically collected call hierarchy data", true, myProject);

    String message;
    if (deleted.get()) {
      message = "Collected call hierarchy data was deleted";
    }
    else {
      message = "Nothing to delete";
    }
    Messages.showInfoMessage(myProject, message, "Delete call hierarchy cache");
  }

  private static VirtualFile getCallerFile(PyHierarchyCallInfo callInfo) {
    return LocalFileSystem.getInstance().findFileByPath(callInfo.getCallerFile());
  }

  private static VirtualFile getCalleeFile(PyHierarchyCallInfo callInfo) {
    return LocalFileSystem.getInstance().findFileByPath(callInfo.getCalleeFile());
  }
}
