package com.intellij.tasks.config;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.Nls;
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
public class TaskRepositoriesConfigurable extends BaseConfigurable {

  private JPanel myPanel;
  private JBList myRepositoriesList;
  @SuppressWarnings({"UnusedDeclaration"})
  private JPanel myToolbarPanel;
  private Splitter mySplitter;
  private JPanel myRepositoryEditor;

  private static final Icon ADD_ICON = IconLoader.getIcon("/general/add.png");

  private final List<TaskRepository> myRepositories = new ArrayList<TaskRepository>();
  private final Project myProject;
  private DefaultActionGroup myActionGroup;

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
  private ActionToolbar myToolbar;

  public TaskRepositoriesConfigurable(final Project project) {

    myManager = (TaskManagerImpl)TaskManager.getManager(project);
    TaskRepositoryType[] groups = TaskManagerImpl.ourRepositoryTypes;

    List<AnAction> createActions = ContainerUtil.map2List(groups, new Function<TaskRepositoryType, AnAction>() {
      public AnAction fun(final TaskRepositoryType group) {
        String text = "New " + group.getName() + " server";
        return new IconWithTextAction(text, text, group.getIcon()) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            TaskRepository repository = group.createRepository();
            addRepository(repository);
          }
        };
      }
    });

    MultipleAddAction addAction = new MultipleAddAction(null, "Add server", createActions);
    addAction.registerCustomShortcutSet(CommonShortcuts.INSERT, myRepositoriesList);
    myActionGroup.add(addAction);
    IconWithTextAction removeAction = new IconWithTextAction(null, "Remove server", IconLoader.getIcon("/general/remove.png")) {

      @Override
      public void actionPerformed(AnActionEvent e) {
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

      @Override
      public void update(AnActionEvent e) {
        TaskRepository repository = getSelectedRepository();
        e.getPresentation().setEnabled(repository != null);
      }
    };
    removeAction.registerCustomShortcutSet(CommonShortcuts.DELETE, myRepositoriesList);
    myActionGroup.add(removeAction);

    myProject = project;

    myRepositoriesList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
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
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        TaskRepository repository = (TaskRepository)value;
        setIcon(repository.getRepositoryType().getIcon());
        append(repository.getPresentableName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    });
    myChangeListener = new Consumer<TaskRepository>() {
      public void consume(TaskRepository repository) {
        ((CollectionListModel)myRepositoriesList.getModel()).contentsChanged(repository);
      }
    };
  }

  private void addRepository(TaskRepository repository) {
    myRepositories.add(repository);
    ((CollectionListModel)myRepositoriesList.getModel()).add(repository);
    addRepositoryEditor(repository, true);
    myRepositoriesList.setSelectedIndex(myRepositoriesList.getModel().getSize() - 1);
  }

  private void addRepositoryEditor(TaskRepository repository, boolean requestFocus) {
    TaskRepositoryEditor editor = repository.getRepositoryType().createEditor(repository, myProject, myChangeListener);
    JComponent component = editor.createComponent();
    String name = myRepoNames.get(repository);
    myRepositoryEditor.add(component, name);
    myRepositoryEditor.doLayout();
    JComponent preferred = editor.getPreferredFocusedComponent();
    if (preferred != null && requestFocus) {
      IdeFocusManager.getInstance(myProject).requestFocus(preferred, false);
    }
  }

  @Nullable
  private TaskRepository getSelectedRepository() {
    return (TaskRepository)myRepositoriesList.getSelectedValue();
  }

  @Nls
  public String getDisplayName() {
    return "Servers";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "reference.settings.project.tasks.servers";
  }

  public JComponent createComponent() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myToolbar.updateActionsImmediately();
      }
    });
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
    List<TaskRepository> newRepositories = ContainerUtil.map(myRepositories, new Function<TaskRepository, TaskRepository>() {
      public TaskRepository fun(TaskRepository taskRepository) {
        return taskRepository.clone();
      }
    });
    myManager.setRepositories(newRepositories);
    myManager.updateIssues(null);
    RecentTaskRepositories.getInstance().addRepositories(myRepositories);
  }

  public void reset() {
    myRepoNames.clear();
    myRepositoryEditor.removeAll();
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
      addRepositoryEditor(clone, false);
    }
    
    if (!myRepositories.isEmpty()) {
      myRepositoriesList.setSelectedValue(myRepositories.get(0), true);
    }
  }

  private List<TaskRepository> getReps() {
    return Arrays.asList(myManager.getAllRepositories());
  }

  public void disposeUIResources() {
  }

  private void createUIComponents() {
    myActionGroup = new DefaultActionGroup();
    myToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, myActionGroup, true);
    myToolbarPanel = (JPanel)myToolbar.getComponent();

    myRepositoriesList = new JBList();
    myRepositoryEditor = new JPanel(new CardLayout());
    mySplitter = new Splitter(false);
    mySplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myRepositoriesList));
    mySplitter.setSecondComponent(myRepositoryEditor);
    mySplitter.setHonorComponentsMinimumSize(true);
    mySplitter.setShowDividerControls(true);

    myRepositoriesList.getEmptyText().setText("No servers");
  }

  private class MultipleAddAction extends IconWithTextAction {
    private final List<AnAction> myAdditional;

    public MultipleAddAction(String text, String description, List<AnAction> additional) {
      super(text, description, ADD_ICON);
      myAdditional = additional;
    }

    public void actionPerformed(AnActionEvent e) {
      DefaultActionGroup group = new DefaultActionGroup();
      for (AnAction aMyAdditional : myAdditional) {
        group.add(aMyAdditional);
      }
      Set<TaskRepository> repositories = RecentTaskRepositories.getInstance().getRepositories();
      repositories.removeAll(myRepositories);
      if (!repositories.isEmpty()) {
        group.add(Separator.getInstance());
        for (final TaskRepository repository : repositories) {
          group.add(new IconWithTextAction(repository.getUrl(), repository.getUrl(), repository.getRepositoryType().getIcon()) {
            @Override
            public void actionPerformed(AnActionEvent e) {
              addRepository(repository);
            }
          });
        }
      }

      ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TODO_VIEW_TOOLBAR, group);

      popupMenu.getComponent().show(createCustomComponent(getTemplatePresentation()), 10, 10);
    }
  }


}
