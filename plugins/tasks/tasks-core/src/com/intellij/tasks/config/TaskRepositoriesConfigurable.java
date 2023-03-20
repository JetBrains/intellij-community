// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.config;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.tasks.*;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
@SuppressWarnings("unchecked")
public class TaskRepositoriesConfigurable implements Configurable.NoScroll, SearchableConfigurable, Configurable.WithEpDependencies {
  public static final String ID = "tasks.servers";
  private static final String EMPTY_PANEL = "empty.panel";

  private JPanel myPanel;
  private JPanel myServersPanel;
  private final JBList<TaskRepository> myRepositoriesList;
  @SuppressWarnings("UnusedDeclaration")
  private JPanel myToolbarPanel;
  private JPanel myRepositoryEditor;
  private JBLabel myServersLabel;
  private Splitter mySplitter;
  private JPanel myEmptyPanel;

  private final List<TaskRepository> myRepositories = new ArrayList<>();
  private final List<TaskRepositoryEditor> myEditors = new ArrayList<>();
  private final Project myProject;

  private final Consumer<TaskRepository> myChangeListener;
  private int count;
  private final Map<TaskRepository, String> myRepoNames = ConcurrentFactoryMap.createMap(repository -> Integer.toString(count++));
  private final TaskManagerImpl myManager;

  public TaskRepositoriesConfigurable(final Project project) {

    myProject = project;
    myManager = (TaskManagerImpl)TaskManager.getManager(project);

    myRepositoriesList = new JBList<>();
    myRepositoriesList.getEmptyText().setText(TaskBundle.message("settings.no.servers"));

    myServersLabel.setLabelFor(myRepositoriesList);

    myServersPanel.setMinimumSize(new Dimension(-1, 100));

    List<TaskRepositoryType<?>> groups = new ArrayList<>(TaskRepositoryType.getRepositoryTypes());
    groups.sort(null);

    final List<AnAction> createActions = new ArrayList<>();
    for (final TaskRepositoryType<?> repositoryType : groups) {
      for (final TaskRepositorySubtype subtype : repositoryType.getAvailableSubtypes()) {
        createActions.add(new AddServerAction(subtype) {
          @Override
          protected TaskRepository getRepository() {
            return repositoryType.createRepository(subtype);
          }
        });
      }
    }

    ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(myRepositoriesList)
      .disableUpDownActions()
      .setAddIcon(LayeredIcon.ADD_WITH_DROPDOWN);

    toolbarDecorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton anActionButton) {
        DefaultActionGroup group = new DefaultActionGroup();
        for (AnAction aMyAdditional : createActions) {
          group.add(aMyAdditional);
        }
        Set<TaskRepository> repositories = RecentTaskRepositories.getInstance().getRepositories();
        myRepositories.forEach(repositories::remove);
        if (!repositories.isEmpty()) {
          group.add(Separator.getInstance());
          for (final TaskRepository repository : repositories) {
            group.add(new AddServerAction(repository) {
              @Override
              protected TaskRepository getRepository() {
                return repository;
              }
            });
          }
        }

        JBPopupFactory.getInstance()
          .createActionGroupPopup(TaskBundle.message("popup.title.add.server"), group, DataManager.getInstance().getDataContext(anActionButton.getContextComponent()),
                                  JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true).show(
          anActionButton.getPreferredPopupPoint());
      }
    });

    toolbarDecorator.setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton anActionButton) {
        TaskRepository repository = getSelectedRepository();
        if (repository != null) {

          CollectionListModel<TaskRepository> model = getListModel();
          model.remove(repository);
          myRepositories.remove(repository);

          if (model.getSize() > 0) {
            myRepositoriesList.setSelectedValue(model.getElementAt(0), true);
          }
          else {
            myRepositoryEditor.removeAll();
            myRepositoryEditor.repaint();
          }
        }
      }
    });

    myServersPanel.add(toolbarDecorator.createPanel(), BorderLayout.CENTER);

    myRepositoriesList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(@NotNull ListSelectionEvent e) {
        TaskRepository repository = getSelectedRepository();
        if (repository != null) {
          String name = myRepoNames.get(repository);
          assert name != null;
          ((CardLayout)myRepositoryEditor.getLayout()).show(myRepositoryEditor, name);
          mySplitter.doLayout();
          mySplitter.repaint();
        }
      }
    });

    myRepositoriesList.setCellRenderer(SimpleListCellRenderer.create((label, value, index) -> {
      label.setIcon(value.getIcon());
      label.setText(value.getPresentableName());
    }));

    myChangeListener = repository -> getListModel().contentsChanged(repository);
  }

  private CollectionListModel<TaskRepository> getListModel() {
    return (CollectionListModel<TaskRepository>)myRepositoriesList.getModel();
  }

  private void addRepository(TaskRepository repository) {
    myRepositories.add(repository);
    getListModel().add(repository);
    addRepositoryEditor(repository);
    myRepositoriesList.setSelectedIndex(myRepositoriesList.getModel().getSize() - 1);
  }

  private void addRepositoryEditor(TaskRepository repository) {
    TaskRepositoryEditor editor = repository.getRepositoryType().createEditor(repository, myProject, myChangeListener);
    myEditors.add(editor);
    JComponent component = editor.createComponent();
    String name = myRepoNames.get(repository);
    myRepositoryEditor.add(component, name);
    myRepositoryEditor.doLayout();
  }

  @Nullable
  private TaskRepository getSelectedRepository() {
    return myRepositoriesList.getSelectedValue();
  }

  @Override
  public String getDisplayName() {
    return TaskBundle.message("configurable.TaskRepositoriesConfigurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "reference.settings.project.tasks.servers";
  }

  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myRepositoriesList;
  }

  @Override
  public boolean isModified() {
    return !myRepositories.equals(Arrays.asList(myManager.getAllRepositories()));
  }

  @Override
  public void apply() {
    List<TaskRepository> newRepositories = ContainerUtil.map(myRepositories, TaskRepository::clone);
    myManager.setRepositories(newRepositories);
    myManager.updateIssues(null);
    RecentTaskRepositories.getInstance().addRepositories(myRepositories);
  }

  @Override
  public void reset() {
    myRepoNames.clear();
    myRepositoryEditor.removeAll();
    myRepositoryEditor.add(myEmptyPanel, EMPTY_PANEL);
//    ((CardLayout)myRepositoryEditor.getLayout()).show(myRepositoryEditor, );
    myRepositories.clear();

    CollectionListModel<TaskRepository> listModel = new CollectionListModel<>(new ArrayList<>());
    for (TaskRepository repository : myManager.getAllRepositories()) {
      TaskRepository clone = repository.clone();
      assert clone.equals(repository) : repository.getClass().getName();
      myRepositories.add(clone);
      listModel.add(clone);
    }

    myRepositoriesList.setModel(listModel);

    for (TaskRepository clone : myRepositories) {
      addRepositoryEditor(clone);
    }

    if (!myRepositories.isEmpty()) {
      myRepositoriesList.setSelectedValue(myRepositories.get(0), true);
    }
  }

  @Override
  public void disposeUIResources() {
    for (TaskRepositoryEditor editor : myEditors) {
      Disposer.dispose(editor);
    }
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    TaskRepository matched = ContainerUtil.find(myRepositories, repository -> repository.getRepositoryType().getName().contains(option));
    return matched == null ? null : () -> myRepositoriesList.setSelectedValue(matched, true);
  }

  @Override
  public @NotNull Collection<BaseExtensionPointName<?>> getDependencies() {
    return Collections.singletonList(TaskRepositoryType.EP_NAME);
  }

  private abstract class AddServerAction extends IconWithTextAction implements DumbAware {

    AddServerAction(TaskRepositorySubtype subtype) {
      super(subtype::getName, TaskBundle.messagePointer("settings.new.server", subtype.getName()), subtype.getIcon());
    }

    AddServerAction(TaskRepository repository) {
      super(repository.getUrl(), repository.getUrl(), repository.getIcon());
    }

    protected abstract TaskRepository getRepository();

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      addRepository(getRepository());
    }
  }
}
