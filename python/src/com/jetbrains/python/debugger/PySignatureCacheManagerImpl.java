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

/**
 * @author traff
 */
public class PySignatureCacheManagerImpl extends PySignatureCacheManager {
  protected static final Logger LOG = Logger.getInstance(PySignatureCacheManagerImpl.class.getName());

  private final static boolean SHOULD_OVERWRITE_TYPES = false;

  public static final FileAttribute CALL_SIGNATURES_ATTRIBUTE = new FileAttribute("call.signatures.attribute", 1, true);

  private final Project myProject;

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

  public PySignatureCacheManagerImpl(Project project) {
    myProject = project;
  }

  @Override
  public void recordSignature(@NotNull PySignature signature) {
    GlobalSearchScope scope = ProjectScope.getProjectScope(myProject);

    VirtualFile file = getFile(signature);
    if (file != null && scope.contains(file)) {
      recordSignature(file, signature);
    }
  }

  private void recordSignature(VirtualFile file, PySignature signature) {
    String dataString = readAttribute(file);

    String[] lines;
    if (dataString != null) {
      lines = dataString.split("\n");
    }
    else {
      lines = ArrayUtil.EMPTY_STRING_ARRAY;
    }

    boolean found = false;
    int i = 0;
    for (String sign : lines) {
      String[] parts = sign.split("\t");
      if (parts.length > 0 && parts[0].equals(signature.getFunctionName())) {
        found = true;

        lines[i] = changeSignatureString(file.getCanonicalPath(), signature, lines[i]);
      }
      i++;
    }
    if (!found) {
      String[] lines2 = new String[lines.length + 1];
      System.arraycopy(lines, 0, lines2, 0, lines.length);

      lines2[lines2.length - 1] = signatureToString(signature);
      lines = lines2;
    }

    String attrString = StringUtil.join(lines, "\n");

    writeAttribute(file, attrString);
  }

  static String changeSignatureString(@NotNull String filePath, @NotNull PySignature signature, @NotNull String oldSignatureString) {
    if (SHOULD_OVERWRITE_TYPES) {
      return signatureToString(signature);
    }
    else {
      //noinspection ConstantConditions
      return signatureToString(stringToSignature(filePath, oldSignatureString).addAllArgs(signature).addReturnType(signature.getReturnTypeQualifiedName()));
    }
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
      CALL_SIGNATURES_ATTRIBUTE.writeAttributeBytes(file, attrString.getBytes());
    }
    catch (IOException e) {
      LOG.warn("Can't write attribute " + file.getCanonicalPath() + " " + attrString);
    }
  }

  @Nullable
  public String findParameterType(@NotNull PyFunction function, @NotNull String name) {
    final PySignature signature = findSignature(function);
    if (signature != null) {
      return signature.getArgTypeQualifiedName(name);
    }
    return null;
  }

  @Nullable
  public PySignature findSignature(@NotNull PyFunction function) {
    VirtualFile file = getFile(function);
    if (file != null) {
      return readSignatureAttributeFromFile(file, getFunctionName(function));
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
  private PySignature readSignatureAttributeFromFile(@NotNull VirtualFile file, @NotNull String name) {
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

  @NotNull
  private static String readAttributeFromFile(@NotNull VirtualFile file) {
    byte[] data;
    try {
      data = CALL_SIGNATURES_ATTRIBUTE.readAttributeBytes(file);
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
  private static PySignature stringToSignature(@NotNull String path, @NotNull String string) {
    String[] parts = string.split("\t");
    if (parts.length > 0) {
      PySignature signature = new PySignature(path, parts[0]);
      for (int i = 1; i < parts.length; i++) {
        String part = parts[i];
        if (part.isEmpty()) {
          continue;
        }
        String[] var = part.split(":");
        if (var.length == 2) {
          if (RETURN_TYPE.equals(var[0])) {
            signature = signature.addReturnType(var[1]);
          }
          else {
            signature = signature.addArgument(var[0], var[1]);
          }
        }
        else {
          throw new IllegalStateException(
            "Should be <name>:<type> format for arg or " + RETURN_TYPE + ":<type> for return type; '" + part + "' instead.");
        }
      }
      return signature;
    }
    return null;
  }

  @Nullable
  private static VirtualFile getFile(@NotNull PySignature signature) {
    return LocalFileSystem.getInstance().refreshAndFindFileByPath(signature.getFile());
  }

  @Nullable
  private static VirtualFile getFile(@NotNull PyFunction function) {
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
    }, "Cleaning the Cache of Dynamically Collected Types", true, myProject);


    String message;
    if (deleted.get()) {
      message = "Collected signatures were deleted";
    }
    else {
      message = "Nothing to delete";
    }
    Messages.showInfoMessage(myProject, message, "Delete Cache");
  }
}
