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
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


public class PyReturnSignatureCacheManagerImpl extends PyReturnSignatureCacheManager{
  public static final Logger Log = Logger.getInstance(PyReturnSignatureCacheManager.class.getName());

  private static final boolean SHOULD_OVERWRITE_TYPES = false;

  public static final FileAttribute RETURN_SIGNATURE_ATTRIBUTE = new FileAttribute("return.signatures.attribute", 1, true);

  private Project myProject;

  private final LoadingCache<VirtualFile, String> mySignatureCache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterAccess(10, TimeUnit.MINUTES)
    .build(
      new CacheLoader<VirtualFile, String>() {
        @Override
        public String load(VirtualFile key) throws Exception {
          return readAttributeFromFile(key);
        }
      });

  public PyReturnSignatureCacheManagerImpl(Project project) {
    myProject = project;
  }

  private static String readAttributeFromFile(@NotNull VirtualFile file) {
    byte[] data;
    try {
      data = RETURN_SIGNATURE_ATTRIBUTE.readAttributeBytes(file);
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

  @Override
  public void recordReturnSignature(@NotNull PyReturnSignature returnSignature) {
    GlobalSearchScope scope = ProjectScope.getProjectScope(myProject);

    VirtualFile file = getFile(returnSignature);
    if (file != null & scope.contains(file)) {
      recordReturnSignature(returnSignature, file);
    }
  }

  @Nullable
  private static VirtualFile getFile(@NotNull PyReturnSignature returnSignature) {
    return LocalFileSystem.getInstance().findFileByPath(returnSignature.getFile());
  }

  @Nullable
  private static VirtualFile getFile(@NotNull PyFunction function) {
    PsiFile file = function.getContainingFile();
    return file != null ? file.getOriginalFile().getVirtualFile() : null;
  }

  private void recordReturnSignature(PyReturnSignature returnSignature, VirtualFile file) {
    String data = readAttribute(file);

    String[] lines;
    if (data != null) {
      lines = data.split("\n");
    }
    else {
      lines = ArrayUtil.EMPTY_STRING_ARRAY;
    }

    boolean found = false;
    int i = 0;
    for (String sign : lines) {
      String[] parts = sign.split("\t");
      if (parts.length > 0 && parts[0].equals(returnSignature.getFunctionName())) {
        found = true;
        if (SHOULD_OVERWRITE_TYPES) {
          lines[i] = signatureToString(returnSignature);
        }
        else {
          //noinspection ConstantConditions
          lines[i] = signatureToString(stringToSignature(file.
            getCanonicalPath(), lines[i]).addAllTypes(returnSignature));
        }
      }
      i++;
    }
    if (!found) {
      String[] lines2 = new String[lines.length + 1];
      System.arraycopy(lines, 0, lines2, 0, lines.length);

      lines2[lines2.length - 1] = signatureToString(returnSignature);
      lines = lines2;
    }

    String attrString = StringUtil.join(lines, "\n");

    writeAttribute(file, attrString);
  }

  @Nullable
  private String readAttribute(@NotNull VirtualFile file) {
    try {
      String attrContent = mySignatureCache.get(file);
      if (!StringUtil.isEmpty(attrContent)) {
        return attrContent;
      }
    }
    catch (ExecutionException e) {
      //pass
    }
    return null;
  }

  private void writeAttribute(@NotNull VirtualFile file, @NotNull String attrString) {
    String cachedValue = mySignatureCache.asMap().get(file);
    if (!attrString.equals(cachedValue)) {
      mySignatureCache.put(file, attrString);
      writeAttributeToAFile(file, attrString);
    }
  }

  private static void writeAttributeToAFile(@NotNull VirtualFile file, @NotNull String attrString) {
    try {
      RETURN_SIGNATURE_ATTRIBUTE.writeAttributeBytes(file, attrString.getBytes());
    }
    catch (IOException e) {
      Log.warn("Can't write attribute " + file.getCanonicalPath() + " " + attrString);
    }
  }

  @Nullable
  @Override
  public String findReturnTypes(@NotNull PyFunction function) {
    final PyReturnSignature signature = findReturnSignature(function);
    if (signature != null) {
      return signature.getReturnTypeQualifiedName();
    }
    return null;
  }

  @Nullable
  @Override
  public PyReturnSignature findReturnSignature(@NotNull PyFunction function) {
    VirtualFile file = getFile(function);
    if (file != null) {
      return readReturnSignatureAttributeFromFile(file, getFunctionName(function));
    }
    else {
      return null;
    }
  }

  private static String getFunctionName(PyFunction function) {
    String name = function.getName();
    if (name == null) {
      return "";
    }

    PyClass cls = function.getContainingClass();

    if (cls != null) {
      name = cls.getName() + "." + name;
    }

    return name;
  }

  @Nullable
  private PyReturnSignature readReturnSignatureAttributeFromFile(@NotNull VirtualFile file, @NotNull String name) {
    String content = readAttribute(file);

    if (content != null) {
      String[] lines = content.split("\n");
      for (String sign : lines) {
        String[] parts = sign.split("\t");
        if (parts.length > 0 && parts[0].equals(name)) {
          return stringToSignature(file.getCanonicalPath(), sign);
        }
      }
    }

    return null;
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
            if (readAttribute(fileOrDir) != null) {
              writeAttribute(fileOrDir, "");
              deleted.set(true);
            }
            if (ProgressManager.getInstance().getProgressIndicator().isCanceled()) {
              return false;
            }
            return true;
          }
        });
      }
    }, "Cleaning the cache of dynamically collected return types", true, myProject);


    String message;
    if (deleted.get()) {
      message = "Collected return signatures were deleted";
    }
    else {
      message = "Nothing to delete";
    }
    Messages.showInfoMessage(myProject, message, "Delete return signature cache");
  }

  @Nullable
  private static PyReturnSignature stringToSignature(String path, String string) {
    String[] parts = string.split("\t");
    if (parts.length > 0) {
      PyReturnSignature signature = new PyReturnSignature(path, parts[0]);
      for (int i = 1; i < parts.length; i++) {
        signature.addType(parts[i]);
      }
      return signature;
    }
    return null;
  }

  private static String signatureToString(PyReturnSignature signature) {
    return signature.getFunctionName() + "\t" + StringUtil.join(signature.getReturnTypes(), "\t");
  }
}

