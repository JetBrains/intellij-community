// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.embedder;

import org.eclipse.aether.repository.RemoteRepository;

import java.util.ArrayList;
import java.util.List;

public class RepositoryDataList extends ArrayList<RepositoryData> {
  RepositoryDataList(List<RemoteRepository> repositories) {
    if (null == repositories) return;
    for (RemoteRepository repo : repositories) {
      RepositoryData data = new RepositoryData(repo);
      add(data);
    }
  }
}
