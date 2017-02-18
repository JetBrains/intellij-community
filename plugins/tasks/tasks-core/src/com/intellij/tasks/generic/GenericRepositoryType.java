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
import icons.TasksIcons;
import org.jdom.Document;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * User: Evgeny.Zakrevsky
 * Date: 10/4/12
 */
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
      if (repository != null) {
        repository.setRepositoryType(GenericRepositoryType.this);
        repository.setSubtypeName(getName());
      }
      return repository;
    }
  }

  // Subtypes:
  public final class AsanaRepository extends GenericSubtype {
    public AsanaRepository() {
      super("Asana", TasksIcons.Asana);
    }
  }

  public final class AssemblaRepository extends GenericSubtype {
    public AssemblaRepository() {
      super("Assembla", TasksIcons.Assembla);
    }
  }

  public final class SprintlyRepository extends GenericSubtype {
    public SprintlyRepository() {
      super("Sprintly", TasksIcons.Sprintly);
    }
  }
}
