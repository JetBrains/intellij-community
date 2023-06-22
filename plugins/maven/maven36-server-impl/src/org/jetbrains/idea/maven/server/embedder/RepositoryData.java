// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.embedder;

import org.eclipse.aether.repository.RemoteRepository;

import java.util.Objects;

class RepositoryData {
  private final String id;
  private final String url;

  RepositoryData(String id, String url) {
    this.id = id;
    this.url = url;
  }

  RepositoryData(RemoteRepository repo) {
    this(repo.getId(), repo.getUrl());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RepositoryData data = (RepositoryData)o;

    if (!Objects.equals(id, data.id)) return false;
    if (!Objects.equals(url, data.url)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (url != null ? url.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "RepositoryData{" +
           "id='" + id + '\'' +
           ", url='" + url + '\'' +
           '}';
  }
}
