/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.model;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class MavenBuildBase implements Serializable {
  private String myFinalName;
  private String myDefaultGoal;
  private String myDirectory;

  private transient @NotNull ReentrantReadWriteLock myLock = new ReentrantReadWriteLock();
  private final @NotNull List<@NotNull String> myFilters = new ArrayList<>();
  private final @NotNull List<@NotNull MavenSource> myMavenSources = new ArrayList<>();

  public String getFinalName() {
    return myFinalName;
  }

  public void setFinalName(String finalName) {
    myFinalName = finalName;
  }

  public String getDefaultGoal() {
    return myDefaultGoal;
  }

  public void setDefaultGoal(String defaultGoal) {
    myDefaultGoal = defaultGoal;
  }

  public String getDirectory() {
    return myDirectory;
  }

  public void setDirectory(String directory) {
    myDirectory = directory;
  }

  public @NotNull List<@NotNull MavenResource> getResources() {
    return withReadLock(() -> myMavenSources.stream()
      .filter(MavenSource::isResource)
      .map(MavenResource::new)
      .collect(Collectors.toList()));
  }

  public void setResources(@NotNull List<@NotNull MavenResource> resources) {
    withWriteLock(() -> {
      myMavenSources.removeIf(MavenSource::isResource);
      List<MavenSource> converted = new ArrayList<>(resources.size());
      for (MavenResource r : resources) {
        converted.add(MavenSource.fromResource(r, false));
      }
      myMavenSources.addAll(converted);
    });
  }

  public @NotNull List<@NotNull MavenResource> getTestResources() {
    return withReadLock(() -> myMavenSources.stream()
      .filter(MavenSource::isTestResource)
      .map(MavenResource::new)
      .collect(Collectors.toList()));
  }

  public void setTestResources(@NotNull List<@NotNull MavenResource> testResources) {
    withWriteLock(() -> {
      myMavenSources.removeIf(MavenSource::isTestResource);
      List<MavenSource> converted = new ArrayList<>(testResources.size());
      for (MavenResource r : testResources) {
        converted.add(MavenSource.fromResource(r, true));
      }
      myMavenSources.addAll(converted);
    });
  }

  public @NotNull List<@NotNull String> getSources() {
    return withReadLock(() -> myMavenSources.stream()
      .filter(MavenSource::isSource)
      .map(MavenSource::getDirectory)
      .collect(Collectors.toList()));
  }

  public void setSources(@NotNull List<@NotNull String> sources) {
    withWriteLock(() -> {
      myMavenSources.removeIf(MavenSource::isSource);
      List<MavenSource> converted = new ArrayList<>(sources.size());
      for (String s : sources) {
        converted.add(MavenSource.fromSrc(s, false));
      }
      myMavenSources.addAll(converted);
    });
  }

  public @NotNull List<@NotNull String> getTestSources() {
    return withReadLock(() -> myMavenSources.stream()
      .filter(MavenSource::isTestSource)
      .map(MavenSource::getDirectory)
      .collect(Collectors.toList()));
  }

  public void setTestSources(@NotNull List<@NotNull String> testSources) {
    withWriteLock(() -> {
      myMavenSources.removeIf(MavenSource::isTestSource);
      List<MavenSource> converted = new ArrayList<>(testSources.size());
      for (String s : testSources) {
        converted.add(MavenSource.fromSrc(s, true));
      }
      myMavenSources.addAll(converted);
    });
  }

  public @NotNull List<@NotNull String> getFilters() {
    return withReadLock(() -> Collections.unmodifiableList(new ArrayList<>(myFilters)));
  }

  public void setFilters(@NotNull List<@NotNull String> filters) {
    withWriteLock(() -> {
      myFilters.clear();
      myFilters.addAll(filters);
    });
  }

  public @NotNull List<@NotNull MavenSource> getMavenSources() {
    return withReadLock(() -> new ArrayList<>(myMavenSources));
  }

  public void setMavenSources(@NotNull List<@NotNull MavenSource> mavenSources) {
    withWriteLock(() -> {
      myMavenSources.clear();
      myMavenSources.addAll(mavenSources);
    });
  }

  private <T> T withReadLock(@NotNull Supplier<T> supplier) {
    myLock.readLock().lock();
    try {
      return supplier.get();
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  private void withWriteLock(@NotNull Runnable runnable) {
    myLock.writeLock().lock();
    try {
      runnable.run();
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  private void readObject(@NotNull ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    myLock = new ReentrantReadWriteLock();
  }
}
