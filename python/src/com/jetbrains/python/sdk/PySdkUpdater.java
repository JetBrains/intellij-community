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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This sdk updater class is a facade to Sdk to make changes in it in a reliable way.
 * Working with sdk instance instead of this class can be wrong, because an instance can become
 * obsolete being substituted in sdk table by a new one. Or already created sdk modificator can be committed,
 * discarding the changes that we doing with the sdk.
 *
 *
 * There are two ways of creation of the facade:
 *  1) by sdk path - in this case we'll get the current actual sdk instance
 * from sdk table by that path, creating and committing SdkModificator on every change
 *  2) by sdkModificator - in that case we'll make changes to that modificator, but it is not committed,
 *  because it has been created outside of the updater.
 *
 *
 * @author traff
 */
public abstract class PySdkUpdater {
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

  public static PySdkUpdater fromSdkPath(@Nullable String sdkPath) {
    return new JdkTableUpdater(sdkPath);
  }

  public static PySdkUpdater fromSdkModificator(@NotNull Sdk sdk, @NotNull SdkModificator sdkModificator) {
    return new SdkModificatorUpdater(sdk, sdkModificator);
  }

  @Nullable
  public abstract String getHomePath();

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

    public void modifySdk(@NotNull SdkModificationProcessor processor) {
      ApplicationManager.getApplication().assertIsDispatchThread();

      Sdk sdk = PythonSdkType.findSdkByPath(mySdkPath);

      if (sdk != null) {
        SdkModificator modificator = getSdk().getSdkModificator();
        processor.process(sdk, modificator);
        modificator.commitChanges();
      }
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

    public void modifySdk(@NotNull SdkModificationProcessor processor) {
      processor.process(getSdk(), myModificator);
    }
  }


  public interface SdkModificationProcessor {
    void process(@NotNull Sdk sdk, @NotNull SdkModificator sdkModificator);
  }

  public class PySdkNotFoundException extends RuntimeException {}
}
