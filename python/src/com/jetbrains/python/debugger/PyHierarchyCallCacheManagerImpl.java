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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  private static final String MODULE_CALLABLE_NAME = "<module>";
  private static final String LAMBDA_CALLABLE_NAME = "lambda";

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
        public String load(@NotNull VirtualFile key) throws Exception {
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
        public String load(@NotNull VirtualFile key) throws Exception {
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

  @Nullable
  private String readCallerAttribute(VirtualFile file) {
    return readAttribute(myHierarchyCallersCache, file);
  }

  @Nullable
  private String readCalleeAttribute(VirtualFile file) {
    return readAttribute(myHierarchyCalleesCache, file);
  }

  @Nullable
  private static String readAttribute(LoadingCache<VirtualFile, String> cache, VirtualFile file) {
    try {
      String attrContent = cache.get(file);
      if (!StringUtil.isEmpty(attrContent)) {
        return attrContent;
      }
    }
    catch (ExecutionException ignored) {
    }

    return null;
  }

  private void writeCallerAttribute(VirtualFile file, String attrString) {
    writeAttribute(myHierarchyCallersCache, file, HIERARCHY_CALLERS_DATA, attrString);
  }

  private void writeCalleeAttribute(VirtualFile file, String attrString) {
    writeAttribute(myHierarchyCalleesCache, file, HIERARCHY_CALLEES_DATA, attrString);
  }

  private static void writeAttribute(LoadingCache<VirtualFile, String> cache, VirtualFile file, FileAttribute fileAttribute, String attrString) {
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
  public void recordHierarchyCallData(@NotNull PyHierarchyCallData callData) {
    GlobalSearchScope scope = ProjectScope.getProjectScope(myProject);

    VirtualFile calleeFile = getCalleeFile(callData);
    if (calleeFile != null && scope.contains(calleeFile)) {
      recordHierarchyCallerData(calleeFile, callData);
    }

    VirtualFile callerFile = getCallerFile(callData);
    if (callerFile != null && scope.contains(callerFile)) {
      recordHierarchyCalleeData(callerFile, callData);
    }
  }

  private void recordHierarchyCallerData(VirtualFile calleeFile, PyHierarchyCallData callData) {
    String data = readAttribute(myHierarchyCallersCache, calleeFile);
    String[] lines;
    if (data != null) {
      lines = data.split("\n");
    }
    else {
      lines = ArrayUtil.EMPTY_STRING_ARRAY;
    }

    String callerFileName = callData.getCallerFile();
    int callerDefLine = callData.getCallerDefLine();
    String callerName = getQualifiedName(callerFileName, callData.getCallerName(), callerDefLine);
    callData.setCallerName(callerName);

    String calleeFileName = callData.getCalleeFile();
    int calleeDefLine = callData.getCalleeDefLine();
    String calleeName = getQualifiedName(calleeFileName, callData.getCalleeName(), calleeDefLine);
    callData.setCalleeName(calleeName);

    boolean found = false;
    int i = 0;
    for (String calls: lines) {
      String[] parts = calls.split("\t");
      if (parts.length > 3
          && parts[0].equals(calleeName) && parts[1].equals(callerFileName) && parts[2].equals(callerName)) {
        found = true;
        lines[i] = PyHierarchyCallDataConverter.hierarchyCallDataToCallerDataString(
          PyHierarchyCallDataConverter.callerDataStringToHierarchyCallData(calleeFileName, lines[i]).addAllCalleeCallLines(callData));
      }
      i++;
    }

    if (!found) {
      String[] lines2 = new String[lines.length + 1];
      System.arraycopy(lines, 0, lines2, 0, lines.length);

      lines2[lines2.length - 1] = PyHierarchyCallDataConverter.hierarchyCallDataToCallerDataString(callData);
      lines = lines2;
    }
    String attrString = StringUtil.join(lines, "\n");
    writeCallerAttribute(calleeFile, attrString);
  }

  private void recordHierarchyCalleeData(VirtualFile callerFile, PyHierarchyCallData callData) {
    String data = readAttribute(myHierarchyCalleesCache, callerFile);

    String[] lines;
    if (data != null) {
      lines = data.split("\n");
    }
    else {
      lines = ArrayUtil.EMPTY_STRING_ARRAY;
    }

    String callerFileName = callData.getCallerFile();
    int callerDefLine = callData.getCallerDefLine();
    String callerName = getQualifiedName(callerFileName, callData.getCallerName(), callerDefLine);
    callData.setCallerName(callerName);

    String calleeFileName = callData.getCalleeFile();
    int calleeDefLine = callData.getCalleeDefLine();
    String calleeName = getQualifiedName(calleeFileName, callData.getCalleeName(), calleeDefLine);
    callData.setCalleeName(calleeName);

    boolean found = false;
    int i = 0;
    for (String calls: lines) {
      String[] parts = calls.split("\t");
      if (parts.length > 4
          && parts[0].equals(callerName) && parts[1].equals(calleeFileName) && parts[2].equals(calleeName)) {
        found = true;
        lines[i] = PyHierarchyCallDataConverter.hierarchyCallDataToCalleeDataString(
          PyHierarchyCallDataConverter.calleeDataStringToHierarchyCallData(callerFileName, lines[i]).addAllCalleeCallLines(callData));
      }
      i++;
    }
    if (!found) {
      String[] lines2 = new String[lines.length + 1];
      System.arraycopy(lines, 0, lines2, 0, lines.length);

      lines2[lines2.length - 1] = PyHierarchyCallDataConverter.hierarchyCallDataToCalleeDataString(callData);
      lines = lines2;
    }
    String attrString = StringUtil.join(lines, "\n");
    writeCalleeAttribute(callerFile, attrString);
  }

  @Override
  public Object[] findCallers(@NotNull PyElement callee) {
    VirtualFile calleeFile = getFile(callee);
    String qualifiedName = callee instanceof PyQualifiedNameOwner ? ((PyQualifiedNameOwner)callee).getQualifiedName() : callee.getName();
    if (calleeFile != null) {
      return readCallersFor(calleeFile, qualifiedName);
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public Object[] findCallees(@NotNull PyElement caller) {
    VirtualFile callerFile = getFile(caller);
    String qualifiedName = caller instanceof PyQualifiedNameOwner ? ((PyQualifiedNameOwner)caller).getQualifiedName() : caller.getName();
    if (callerFile != null) {
      return readCalleesFor(callerFile, qualifiedName);
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private Object[] readCallersFor(VirtualFile calleeFile, @Nullable String calleeName) {
    String content = readAttribute(myHierarchyCallersCache, calleeFile);
    if (content == null) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    List<PyHierarchyCallData> callers = Lists.newArrayList();
    String[] lines = content.split("\n");
    for (String callsInfo: lines) {
      String[] parts = callsInfo.split("\t");
      if (calleeName != null && calleeName.equals(parts[0])) {
        callers.add(PyHierarchyCallDataConverter.callerDataStringToHierarchyCallData(calleeFile.getCanonicalPath(), callsInfo));
      }
    }
    return ArrayUtil.toObjectArray(callers);
  }

  private Object[] readCalleesFor(VirtualFile callerFile, @Nullable String callerName) {
    String content = readAttribute(myHierarchyCalleesCache, callerFile);
    if (content == null) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    List<PyHierarchyCallData> callees = Lists.newArrayList();
    String[] lines = content.split("\n");
    for (String callsInfo: lines) {
      String[] parts = callsInfo.split("\t");
      if (callerName != null && callerName.equals(parts[0])) {
        callees.add(PyHierarchyCallDataConverter.calleeDataStringToHierarchyCallData(callerFile.getCanonicalPath(), callsInfo));
      }
    }
    return ArrayUtil.toObjectArray(callees);
  }

  @Nullable
  public static VirtualFile getFile(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();

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
    Messages.showInfoMessage(myProject, message, "Delete Call Hierarchy Cache");
  }

  @Nullable
  private static VirtualFile getCallerFile(PyHierarchyCallData callInfo) {
    return LocalFileSystem.getInstance().findFileByPath(callInfo.getCallerFile());
  }

  @Nullable
  private static VirtualFile getCalleeFile(PyHierarchyCallData callInfo) {
    return LocalFileSystem.getInstance().findFileByPath(callInfo.getCalleeFile());
  }

  public Project getProject() {
    return myProject;
  }

  private String getQualifiedName(final String fileName, final String callableName, final int callableDefLine) {
    VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fileName);
    if (vFile != null) {
      if (callableName.equals(MODULE_CALLABLE_NAME)) {
        return vFile.getName();
      }

      final Document document = FileDocumentManager.getInstance().getDocument(vFile);
      if (document != null) {
        String qualifiedName = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
          @Nullable
          @Override
          public String compute() {
            PsiFile file =  PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
            if (file instanceof PyFile) {
              int lineStart = document.getLineStartOffset(callableDefLine);
              int lineEnd = document.getLineEndOffset(callableDefLine);

              PsiElement element;
              int offset = lineStart;
              while (offset < lineEnd) {
                element = file.findElementAt(offset);
                if (!(element instanceof PsiWhiteSpace)) {
                  PsiElement callable = PsiTreeUtil.getParentOfType(element, PyClass.class, PyFunction.class);
                  if (callable instanceof PyQualifiedNameOwner) {
                    String qualifiedName = ((PyQualifiedNameOwner)callable).getQualifiedName();
                    if (qualifiedName != null) {
                      return qualifiedName;
                    }
                  }
                }
                offset++;
              }
            }
            return null;
          }
        });

        if (qualifiedName != null) {
          return qualifiedName;
        }
      }
    }
    return callableName;
  }
}
