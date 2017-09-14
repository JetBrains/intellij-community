/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.psi.impl.stubs;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.HashCode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.stubs.*;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.IndexInfrastructure;
import com.intellij.util.io.PersistentHashMap;
import com.jetbrains.python.psi.PyFileElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author traff
 */
public class PyPrebuiltStubsProvider implements PrebuiltStubsProvider, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.psi.impl.stubs.PyPrebuiltStubsProvider");
  public static final String PREBUILT_INDICES_PATH_PROPERTY = "prebuilt_indices_path";

  public static final String SDK_STUBS_STORAGE_NAME = "sdk-stubs";

  private final FileContentHashing myFileContentHashing = new FileContentHashing();

  private PersistentHashMap<HashCode, SerializedStubTree> myPrebuiltStubsStorage;
  private SerializationManagerImpl mySerializationManager;

  public PyPrebuiltStubsProvider() {
    init();
  }

  @VisibleForTesting
  void init() {
    File indexesRoot = findPrebuiltIndicesRoot();
    try {
      if (indexesRoot != null) {
        // we should copy prebuilt indexes to a writable folder
        indexesRoot = copyPrebuiltIndexesToIndexRoot(indexesRoot);
        // otherwise we can get access denied error, because persistent hash map opens file for read and write

        String stubVersion = FileUtil.loadFile(new File(indexesRoot, SDK_STUBS_STORAGE_NAME + ".version"));

        int currentVersion = PyFileElementType.INSTANCE.getStubVersion();
        if (Integer.parseInt(stubVersion) == currentVersion) {
          myPrebuiltStubsStorage =
            new PersistentHashMap<HashCode, SerializedStubTree>(new File(indexesRoot, SDK_STUBS_STORAGE_NAME + ".input"),
                                                                HashCodeDescriptor.Companion.getInstance(),
                                                                new StubTreeExternalizer()) {
              @Override
              protected boolean isReadOnly() {
                return true;
              }
            };

          mySerializationManager = new SerializationManagerImpl(new File(indexesRoot, SDK_STUBS_STORAGE_NAME + ".names"));

          Disposer.register(this, mySerializationManager);

          LOG.info("Using prebuilt stubs from " + myPrebuiltStubsStorage.getBaseFile().getAbsolutePath());
        }
        else {
          LOG.error("Prebuilt stubs version mismatch: " + stubVersion + ", current version is " + currentVersion);
        }
      }
    }
    catch (Exception e) {
      myPrebuiltStubsStorage = null;
      LOG.warn("Prebuilt stubs can't be loaded at " + indexesRoot, e);
    }
  }

  private static File copyPrebuiltIndexesToIndexRoot(File prebuiltIndicesRoot) throws IOException {
    File indexRoot = new File(IndexInfrastructure.getPersistentIndexRoot(), "prebuilt");

    FileUtil.copyDir(prebuiltIndicesRoot, indexRoot);

    return indexRoot;
  }


  @Nullable
  @Override
  public Stub findStub(@NotNull FileContent fileContent) {
    if (myPrebuiltStubsStorage != null) {
      HashCode hashCode = myFileContentHashing.hashString(fileContent);
      Stub stub = null;
      try {
        SerializedStubTree stubTree = myPrebuiltStubsStorage.get(hashCode);
        if (stubTree != null) {
          stub = stubTree.getStub(false, mySerializationManager);
        }
      }
      catch (SerializerNotFoundException e) {
        LOG.error("Can't deserialize stub tree", e);
      }
      catch (Exception e) {
        LOG.error("Error reading prebuilt stubs from " + myPrebuiltStubsStorage.getBaseFile().getPath(), e);
        myPrebuiltStubsStorage = null;
        stub = null;
      }
      if (stub != null) {
        return stub;
      }
    }
    return null;
  }

  @Nullable
  private static File findPrebuiltIndicesRoot() {
    String path = System.getProperty(PREBUILT_INDICES_PATH_PROPERTY);
    if (path != null && new File(path).exists()) {
      return new File(path);
    }
    path = PathManager.getHomePath();
    File f = new File(path, "python/index");  // from sources
    if (f.exists()) return f;
    f = new File(path, "index");              // compiled binary
    if (f.exists()) return f;
    return null;
  }

  @Override
  public void dispose() {
    if (myPrebuiltStubsStorage != null) {
      try {
        myPrebuiltStubsStorage.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }
}
