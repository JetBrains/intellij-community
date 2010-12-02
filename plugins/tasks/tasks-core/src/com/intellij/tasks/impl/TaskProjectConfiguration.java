package com.intellij.tasks.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.Condition;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.ArrayList;
import java.util.List;

/**
* @author Dmitry Avdeev
*/
@State(
  name = "TaskProjectConfiguration",
  storages = {@Storage(id = "default", file = "$PROJECT_FILE$")})
public class TaskProjectConfiguration implements PersistentStateComponent<TaskProjectConfiguration> {

  @Tag("server")
  public static class SharedServer {
    @Attribute("type")
    public String type;
    @Attribute("url")
    public String url;
  }

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false, elementTag = "server")
  public List<SharedServer> servers = new ArrayList<SharedServer>();

  private final TaskManagerImpl myManager;

  // for serialization
  public TaskProjectConfiguration() {
    myManager = null;
  }

  public TaskProjectConfiguration(TaskManagerImpl manager) {
    myManager = manager;
  }

  public TaskProjectConfiguration getState() {
    servers.clear();
      for (TaskRepository repository : myManager.getAllRepositories()) {
        if (repository.isShared()) {
          SharedServer server = new SharedServer();
          server.type = repository.getRepositoryType().getName();
          server.url = repository.getUrl();
          servers.add(server);
        }
      }
    return this;
  }

  public void loadState(TaskProjectConfiguration state) {
    for (final SharedServer server : state.servers) {
      if (server.url == null || server.type == null) {
        continue;
      }
      final TaskRepositoryType type = ContainerUtil.find(TaskManager.ourRepositoryTypes, new Condition<TaskRepositoryType>() {
        public boolean value(TaskRepositoryType taskRepositoryType) {
          return taskRepositoryType.getName().equals(server.type);
        }
      });
      if (type != null) {
        TaskRepository repository = ContainerUtil.find(myManager.getAllRepositories(), new Condition<TaskRepository>() {
          public boolean value(TaskRepository taskRepository) {
            return type.equals(taskRepository.getRepositoryType()) && server.url.equals(taskRepository.getUrl());
          }
        });
        if (repository == null) {
          repository = type.createRepository();
          repository.setUrl(server.url);
          repository.setShared(true);
        }
      }
    }
  }

}
