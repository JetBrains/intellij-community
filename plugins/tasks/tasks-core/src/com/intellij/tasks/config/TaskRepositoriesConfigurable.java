package com.intellij.tasks.config;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskRepositorySubtype;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
@SuppressWarnings("unchecked")
public class TaskRepositoriesConfigurable extends BaseConfigurable implements Configurable.NoScroll {

  private static final String EMPTY_PANEL = "empty.panel";
  private JPanel myPanel;
  private JPanel myServersPanel;
  private final JBList myRepositoriesList;
  @SuppressWarnings({"UnusedDeclaration"})
  private JPanel myToolbarPanel;
  private JPanel myRepositoryEditor;
  private JBLabel myServersLabel;
  private Splitter mySplitter;
  private JPanel myEmptyPanel;

  private final List<TaskRepository> myRepositories = new ArrayList<>();
  private final List<TaskRepositoryEditor> myEditors = new ArrayList<>();
  private final Project myProject;

  private final Consumer<TaskRepository> myChangeListener;
  @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
  private final FactoryMap<TaskRepository, String> myRepoNames = new ConcurrentFactoryMap<TaskRepository, String>() {

    private int count;
    @Override
    protected String create(TaskRepository repository) {
      return Integer.toString(count++);
    }
  };
  private final TaskManagerImpl myManager;

  public TaskRepositoriesConfigurable(final Project project) {

    myProject = project;
    myManager = (TaskManagerImpl)TaskManager.getManager(project);

    myRepositoriesList = new JBList();
    myRepositoriesList.getEmptyText().setText("No servers");

    myServersLabel.setLabelFor(myRepositoriesList);

    TaskRepositoryType[] groups = TaskRepositoryType.getRepositoryTypes();

    final List<AnAction> createActions = new ArrayList<>();
    for (final TaskRepositoryType repositoryType : groups) {
      for (final TaskRepositorySubtype subtype : (List<TaskRepositorySubtype>)repositoryType.getAvailableSubtypes()) {
        createActions.add(new AddServerAction(subtype) {
          @Override
          protected TaskRepository getRepository() {
            return repositoryType.createRepository(subtype);
          }
        });
      }
    }

    ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(myRepositoriesList).disableUpDownActions();

    toolbarDecorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton anActionButton) {
        DefaultActionGroup group = new DefaultActionGroup();
        for (AnAction aMyAdditional : createActions) {
          group.add(aMyAdditional);
        }
        Set<TaskRepository> repositories = RecentTaskRepositories.getInstance().getRepositories();
        repositories.removeAll(myRepositories);
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
          .createActionGroupPopup("Add server", group, DataManager.getInstance().getDataContext(anActionButton.getContextComponent()),
                                  JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true).show(
          anActionButton.getPreferredPopupPoint());
      }
    });

    toolbarDecorator.setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton anActionButton) {
        TaskRepository repository = getSelectedRepository();
        if (repository != null) {

          CollectionListModel model = (CollectionListModel)myRepositoriesList.getModel();
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

    myRepositoriesList.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
        TaskRepository repository = (TaskRepository)value;
        setIcon(repository.getIcon());
        append(repository.getPresentableName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    });

    myChangeListener = repository -> ((CollectionListModel)myRepositoriesList.getModel()).contentsChanged(repository);
  }

  private void addRepository(TaskRepository repository) {
    myRepositories.add(repository);
    ((CollectionListModel)myRepositoriesList.getModel()).add(repository);
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
    return (TaskRepository)myRepositoriesList.getSelectedValue();
  }

  @Nls
  public String getDisplayName() {
    return "Servers";
  }

  public String getHelpTopic() {
    return "reference.settings.project.tasks.servers";
  }

  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myRepositoriesList;
  }

  public boolean isModified() {
    return !myRepositories.equals(getReps());
  }

  public void apply() throws ConfigurationException {
    List<TaskRepository> newRepositories = ContainerUtil.map(myRepositories, taskRepository -> taskRepository.clone());
    myManager.setRepositories(newRepositories);
    myManager.updateIssues(null);
    RecentTaskRepositories.getInstance().addRepositories(myRepositories);
  }

  public void reset() {
    myRepoNames.clear();
    myRepositoryEditor.removeAll();
    myRepositoryEditor.add(myEmptyPanel, EMPTY_PANEL);
//    ((CardLayout)myRepositoryEditor.getLayout()).show(myRepositoryEditor, );
    myRepositories.clear();

    CollectionListModel listModel = new CollectionListModel(new ArrayList());
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

  private List<TaskRepository> getReps() {
    return Arrays.asList(myManager.getAllRepositories());
  }

  public void disposeUIResources() {
    for (TaskRepositoryEditor editor : myEditors) {
      Disposer.dispose(editor);
    }
  }

  private abstract class AddServerAction extends IconWithTextAction implements DumbAware {

    public AddServerAction(TaskRepositorySubtype subtype) {
      super(subtype.getName(), "New " + subtype.getName() + " server", subtype.getIcon());
    }

    public AddServerAction(TaskRepository repository) {
      super(repository.getUrl(), repository.getUrl(), repository.getIcon());
    }

    protected abstract TaskRepository getRepository();

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      addRepository(getRepository());
    }
  }
}
