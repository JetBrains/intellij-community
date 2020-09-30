// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.server.security.MavenToken;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.server.UnicastRemoteObject;
import java.util.UUID;

public abstract class MavenRemoteObjectWrapper<T> extends RemoteObjectWrapper<T> {

  public static final MavenToken ourToken = new MavenToken(UUID.randomUUID().toString());

  protected MavenRemoteObjectWrapper(@Nullable RemoteObjectWrapper<?> parent) {
    super(parent);
  }

  static <Some extends MavenRemoteObject> Some doWrapAndExport(Some object) {
    try {
      Remote remote = UnicastRemoteObject
        .exportObject(object, 0);
      if (remote == null) {
        return null;
      }
      return object;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static MavenServerProgressIndicator wrapAndExport(final MavenProgressIndicator indicator) {
    return doWrapAndExport(new RemoteMavenServerProgressIndicator(indicator));
  }

  private static class RemoteMavenServerProgressIndicator extends MavenRemoteObject implements MavenServerProgressIndicator {
    private final MavenProgressIndicator myProcess;

    RemoteMavenServerProgressIndicator(MavenProgressIndicator process) {
      myProcess = process;
    }

    @Override
    public void setText(@NlsContexts.ProgressText String text) {
      myProcess.setText(text);
    }

    @Override
    public void setText2(@NlsContexts.ProgressDetails String text) {
      myProcess.setText2(text);
    }

    @Override
    public void startedDownload(ResolveType type, String dependencyId) {
      myProcess.startedDownload(type, dependencyId);
    }

    @Override
    public void completedDownload(ResolveType type, String dependencyId) {
      myProcess.completedDownload(type, dependencyId);
    }

    @Override
    public void failedDownload(ResolveType type, String dependencyId, String errorMessage, String stackTrace) {
      myProcess.failedDownload(type, dependencyId, errorMessage, stackTrace);
    }

    @Override
    public boolean isCanceled() {
      return myProcess.isCanceled();
    }

    @Override
    public void setIndeterminate(boolean value) {
      myProcess.getIndicator().setIndeterminate(value);
    }

    @Override
    public void setFraction(double fraction) {
      myProcess.setFraction(fraction);
    }
  }
}
