// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.config;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Avdeev
 */
@State(name = "RecentTaskRepositories", storages = @Storage(StoragePathMacros.NON_ROAMABLE_FILE))
@Service(Service.Level.APP)
public final class RecentTaskRepositories implements PersistentStateComponent<Element>, Disposable {
  private final Set<TaskRepository> myRepositories = CollectionFactory.createCustomHashingStrategySet(HASHING_STRATEGY);

  private static final HashingStrategy<TaskRepository> HASHING_STRATEGY = new HashingStrategy<>() {
    @Override
    public int hashCode(@Nullable TaskRepository object) {
      return object == null || object.getUrl() == null ? 0 : object.getUrl().hashCode();
    }

    @Override
    public boolean equals(TaskRepository o1, TaskRepository o2) {
      return o1 == o2 || (o1 != null && o2 != null && Objects.equals(o1.getUrl(), o2.getUrl()));
    }
  };

  public RecentTaskRepositories() {
    // remove repositories pertaining to non-existent types
    TaskRepositoryType.addEPListChangeListener(this, () -> {
      List<Class<?>> possibleRepositoryClasses = TaskRepositoryType.getRepositoryClasses();
      myRepositories.removeIf(repository -> {
        return !ContainerUtil.exists(possibleRepositoryClasses, clazz -> clazz.isAssignableFrom(repository.getClass()));
      });
    });
  }

  public static RecentTaskRepositories getInstance() {
    return ApplicationManager.getApplication().getService(RecentTaskRepositories.class);
  }

  public Set<TaskRepository> getRepositories() {
    Set<TaskRepository> set = CollectionFactory.createCustomHashingStrategySet(HASHING_STRATEGY);
    set.addAll(ContainerUtil.findAll(myRepositories, repository -> !StringUtil.isEmptyOrSpaces(repository.getUrl())));
    return set;
  }

  public void addRepositories(Collection<TaskRepository> repositories) {
    Collection<TaskRepository> old = new ArrayList<>(myRepositories);
    myRepositories.clear();
    if (doAddReps(repositories)) return;
    doAddReps(old);
  }

  private boolean doAddReps(Collection<TaskRepository> repositories) {
    for (TaskRepository repository : repositories) {
      if (!StringUtil.isEmptyOrSpaces(repository.getUrl())) {
        if (myRepositories.size() == 10) {
          return true;
        }
        myRepositories.add(repository);
      }
    }
    return false;
  }

  @Override
  public Element getState() {
    return XmlSerializer.serialize(myRepositories.toArray(new TaskRepository[0]));
  }

  @Override
  public void loadState(@NotNull Element state) {
    myRepositories.clear();
    myRepositories.addAll(TaskManagerImpl.loadRepositories(state));
  }

  @Override
  public void dispose() {}
}
