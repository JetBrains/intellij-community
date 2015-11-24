/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.sdk;

import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * This sdk updater class is a facade to Sdk to make changes in it in a reliable way.
 * Working with sdk instance instead of this class can be wrong, because an instance can become
 * obsolete being substituted in sdk table by a new one. Or already created sdk modificator can be committed,
 * discarding the changes that we doing with the sdk.
 * <p/>
 * <p/>
 * There are two ways of creation of the facade:
 * 1) by sdk path - in this case we'll get the current actual sdk instance
 * from sdk table by that path, creating and committing SdkModificator on every change
 * 2) by sdkModificator - in that case we'll make changes to that modificator, but it is not committed,
 * because it has been created outside of the updater.
 *
 * @author traff
 */
public abstract class PySdkUpdater {
  private static final Logger LOG = Logger.getInstance("#" + PySdkUpdater.class.getName());
  
  private static Map<String, PySdkUpdater> mySdkUpdaters = Maps.newHashMap();

  @NotNull
  public abstract Sdk getSdk();

  public abstract void modifySdk(@NotNull SdkModificationProcessor processor);

  public void addRoot(@NotNull final VirtualFile root, @NotNull final OrderRootType rootType) {
    modifySdk(new SdkModificationProcessor() {
      @Override
      public void process(@NotNull Sdk sdk, @NotNull SdkModificator sdkModificator) {
        sdkModificator.addRoot(root, rootType);
      }
    });
  }

  public void removeRoots(@NotNull final OrderRootType rootType) {
    modifySdk(new PySdkUpdater.SdkModificationProcessor() {
      @Override
      public void process(@NotNull Sdk sdk, @NotNull SdkModificator sdkModificator) {
        sdkModificator.removeRoots(rootType);
      }
    });
  }

  public abstract void commit();

  public static synchronized PySdkUpdater fromSdkPath(@Nullable String sdkPath) {
    checkSingleton(sdkPath);
    return new JdkTableUpdater(sdkPath);
  }

  public static synchronized PySdkUpdater fromSdkModificator(@NotNull Sdk sdk, @NotNull SdkModificator sdkModificator) {
    checkSingleton(sdk.getHomePath());
    return new SdkModificatorUpdater(sdk, sdkModificator);
  }

  public static synchronized PySdkUpdater singletonJdkTableUpdater(@NotNull String sdkPath) {
    if (mySdkUpdaters.get(sdkPath) == null) {
      Sdk sdk = PythonSdkType.findSdkByPath(sdkPath);
      if (sdk != null) {
        mySdkUpdaters.put(sdkPath, new SingletonSdkModificatorUpdater(sdk, sdk.getSdkModificator()));
      } else {
        throw new PySdkNotFoundException();
      }
    }
    return mySdkUpdaters.get(sdkPath);
  }


  private static synchronized void checkSingleton(String sdkPath) {
    if (mySdkUpdaters.get(sdkPath) != null) {
      LOG.error("Changing more then one sdkModificator at a time");
    }
  }

  private static synchronized void checkNotDisposed(String sdkPath) {
    if (mySdkUpdaters.get(sdkPath) == null) {
      LOG.error("sdk modificator is already committed, further changes will be discarded");
    }
  }

  private static synchronized void disposeSingleton(String sdkPath) {
    mySdkUpdaters.put(sdkPath, null);
  }

  @Nullable
  public abstract String getHomePath();

  public abstract VirtualFile[] getRoots(OrderRootType rootType);

  private static class JdkTableUpdater extends PySdkUpdater {
    private final String mySdkPath;

    private JdkTableUpdater(@Nullable String path) {
      mySdkPath = path;
    }

    @NotNull
    @Override
    public Sdk getSdk() {
      Sdk sdk = PythonSdkType.findSdkByPath(mySdkPath);
      if (sdk != null) {
        return sdk;
      }
      else {
        throw new PySdkNotFoundException();
      }
    }

    @Nullable
    @Override
    public String getHomePath() {
      return mySdkPath;
    }

    @Override
    public VirtualFile[] getRoots(OrderRootType rootType) {
      return getSdk().getRootProvider().getFiles(rootType);
    }

    public void modifySdk(@NotNull SdkModificationProcessor processor) {
      checkSingleton(mySdkPath);
      
      ApplicationManager.getApplication().assertIsDispatchThread();

      Sdk sdk = PythonSdkType.findSdkByPath(mySdkPath);

      if (sdk != null) {
        SdkModificator modificator = getSdk().getSdkModificator();
        processor.process(sdk, modificator);
        modificator.commitChanges();
      }
    }

    @Override
    public void commit() {
      // all changes are already committed 
    }
  }

  private static class SdkModificatorUpdater extends PySdkUpdater {
    private SdkModificator myModificator;
    private Sdk mySdk;

    public SdkModificatorUpdater(@NotNull Sdk sdk, @NotNull SdkModificator modificator) {
      mySdk = sdk;
      myModificator = modificator;
    }

    @NotNull
    @Override
    public Sdk getSdk() {
      return mySdk;
    }

    @NotNull
    @Override
    public String getHomePath() {
      return mySdk.getHomePath();
    }

    @Override
    public VirtualFile[] getRoots(OrderRootType rootType) {
      return myModificator.getRoots(rootType);
    }

    public void modifySdk(@NotNull SdkModificationProcessor processor) {
      processor.process(getSdk(), myModificator);
    }

    @Override
    public void commit() {
      checkSingleton(getHomePath());
      myModificator.commitChanges();
    }
  }

  private static class SingletonSdkModificatorUpdater extends SdkModificatorUpdater {
    public SingletonSdkModificatorUpdater(@NotNull Sdk sdk, @NotNull SdkModificator modificator) {
      super(sdk, modificator);
    }

    @Override
    public void modifySdk(@NotNull SdkModificationProcessor processor) {
      checkNotDisposed(getHomePath());
      super.modifySdk(processor);
    }

    @Override
    public void commit() {
      disposeSingleton(getHomePath());
      super.commit();
    }
  }


  public interface SdkModificationProcessor {
    void process(@NotNull Sdk sdk, @NotNull SdkModificator sdkModificator);
  }

  public static class PySdkNotFoundException extends RuntimeException {
  }
}
