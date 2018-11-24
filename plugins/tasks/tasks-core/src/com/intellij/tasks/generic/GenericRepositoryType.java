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
package com.intellij.tasks.generic;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskRepositorySubtype;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import com.intellij.util.xmlb.XmlSerializer;
import icons.TasksCoreIcons;
import org.jdom.Document;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class GenericRepositoryType extends BaseRepositoryType<GenericRepository> {

  @NotNull
  @Override
  public String getName() {
    return "Generic";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return AllIcons.General.Web;
  }

  @NotNull
  @Override
  public TaskRepository createRepository() {
    return new GenericRepository(this);
  }

  @Override
  public Class<GenericRepository> getRepositoryClass() {
    return GenericRepository.class;
  }

  @NotNull
  @Override
  public TaskRepositoryEditor createEditor(final GenericRepository repository,
                                           final Project project,
                                           final Consumer<GenericRepository> changeListener) {
    return new GenericRepositoryEditor<>(project, repository, changeListener);
  }

  @Override
  public List<TaskRepositorySubtype> getAvailableSubtypes() {
    return Arrays.asList(
      this,
      new AsanaRepository(),
      new AssemblaRepository(),
      new SprintlyRepository()
    );
  }

  public class GenericSubtype implements TaskRepositorySubtype {
    private final String myName;
    private final Icon myIcon;

    GenericSubtype(String name, Icon icon) {
      myName = name;
      myIcon = icon;
    }

    @Override
    public String getName() {
      return myName + " [G]";
    }

    @Override
    public Icon getIcon() {
      return myIcon;
    }

    @Override
    public TaskRepository createRepository() {
      Document document;
      try {
        String configFileName = myName.toLowerCase() + ".xml";
        //URL resourceUrl = ResourceUtil.getResource(GenericRepositoryType.class, "connectors", configFileName);
        URL resourceUrl = GenericRepository.class.getResource("connectors/" + configFileName);
        if (resourceUrl == null) {
          throw new AssertionError("Repository configuration file '" + configFileName + "' not found");
        }
        document = JDOMUtil.loadResourceDocument(resourceUrl);
      }
      catch (Exception e) {
        throw new AssertionError(e);
      }
      GenericRepository repository = XmlSerializer.deserialize(document.getRootElement(), GenericRepository.class);
      repository.setRepositoryType(GenericRepositoryType.this);
      repository.setSubtypeName(getName());
      return repository;
    }
  }

  // Subtypes:
  public final class AsanaRepository extends GenericSubtype {
    public AsanaRepository() {
      super("Asana", TasksCoreIcons.Asana);
    }
  }

  public final class AssemblaRepository extends GenericSubtype {
    public AssemblaRepository() {
      super("Assembla", TasksCoreIcons.Assembla);
    }
  }

  public final class SprintlyRepository extends GenericSubtype {
    public SprintlyRepository() {
      super("Sprintly", TasksCoreIcons.Sprintly);
    }
  }
}
