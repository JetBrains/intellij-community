package com.intellij.tasks.impl;

import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.tasks.*;
import com.intellij.tasks.config.TaskRepositoriesConfigurable;
import com.intellij.tasks.context.WorkingContextManager;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Function;
import com.intellij.util.containers.ConcurrentHashSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.xmlb.XmlSerializationException;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Timer;
import javax.swing.event.HyperlinkEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/**
 * @author Dmitry Avdeev
 */
@State(
  name = "TaskManager",
  storages = {
    @Storage(id = "other", file = "$WORKSPACE_FILE$")
  }
)
public class TaskManagerImpl extends TaskManager implements ProjectComponent, PersistentStateComponent<TaskManagerImpl.Config>,
                                                            ChangeListDecorator {

  private static final Logger LOG = Logger.getInstance("#com.intellij.tasks.impl.TaskManagerImpl");

  private static final DecimalFormat LOCAL_TASK_ID_FORMAT = new DecimalFormat("LOCAL-00000");
  public static final Comparator<Task> TASK_UPDATE_COMPARATOR = new Comparator<Task>() {
    public int compare(Task o1, Task o2) {
      int i = Comparing.compare(o2.getUpdated(), o1.getUpdated());
      return i == 0 ? Comparing.compare(o2.getCreated(), o1.getCreated()) : i;
    }
  };
  private static final Convertor<Task,String> KEY_CONVERTOR = new Convertor<Task, String>() {
    @Override
    public String convert(Task o) {
      return o.getId();
    }
  };
  private static final String TASKS_NOTIFICATION_GROUP = "Task Group";

  private final Project myProject;

  private final WorkingContextManager myContextManager;

  private final Map<String,Task> myIssueCache = Collections.synchronizedMap(new HashMap<String,Task>());
  private final Map<String,Task> myTemporaryCache = Collections.synchronizedMap(new HashMap<String,Task>());

  private final Map<String, LocalTaskImpl> myTasks = Collections.synchronizedMap(new LinkedHashMap<String, LocalTaskImpl>() {
    @Override
    public LocalTaskImpl put(String key, LocalTaskImpl task) {
      LocalTaskImpl result = super.put(key, task);
      if (size() > myConfig.taskHistoryLength) {
        ArrayList<LocalTask> list = new ArrayList<LocalTask>(values());
        Collections.sort(list, TASK_UPDATE_COMPARATOR);
        for (LocalTask oldest : list) {
          if (!oldest.isDefault()) {
            remove(oldest);
            break;
          }
        }
      }
      return result;
    }
  });

  @NotNull
  private LocalTask myActiveTask = createDefaultTask();
  private Timer myCacheRefreshTimer;

  private volatile boolean myUpdating;
  private final Config myConfig = new Config();
  private final ChangeListAdapter myChangeListListener;
  private final ChangeListManager myChangeListManager;

  private final List<TaskRepository> myRepositories = new ArrayList<TaskRepository>();
  private final EventDispatcher<TaskListener> myDispatcher = EventDispatcher.create(TaskListener.class);
  private Set<TaskRepository> myBadRepositories = new ConcurrentHashSet<TaskRepository>();

  public TaskManagerImpl(Project project,
                         WorkingContextManager contextManager,
                         final ChangeListManager changeListManager) {

    myProject = project;
    myContextManager = contextManager;
    myChangeListManager = changeListManager;

    myChangeListListener = new ChangeListAdapter() {
      @Override
      public void changeListAdded(ChangeList list) {
        getOpenChangelists(myActiveTask).add(new ChangeListInfo((LocalChangeList)list));
      }

      @Override
      public void changeListRemoved(ChangeList list) {
        getOpenChangelists(myActiveTask).remove(new ChangeListInfo((LocalChangeList)list));
      }

      @Override
      public void defaultListChanged(ChangeList oldDefaultList, ChangeList newDefaultList) {

        final LocalTask associatedTask = getAssociatedTask((LocalChangeList)newDefaultList);
        if (associatedTask != null) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              associatedTask.setUpdated(new Date());
              activateTask(associatedTask, true, false);              
            }
          });
          return;
        }

        final LocalTask task = findTaskByChangelist(newDefaultList);
        if (task != null) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              saveActiveTask();
              doActivate(task, true);
            }
          }, myProject.getDisposed());
        }
      }
    };
  }

  @Nullable
  private LocalTask findTaskByChangelist(ChangeList newDefaultList) {
    ChangeListInfo info = new ChangeListInfo((LocalChangeList)newDefaultList);
    for (final LocalTask task : getLocalTasks()) {
      if (((LocalTaskImpl)task).getChangeLists().contains(info)) {
        return task;
      }
    }
    return null;
  }

  @Override
  public TaskRepository[] getAllRepositories() {
    return myRepositories.toArray(new TaskRepository[myRepositories.size()]);
  }

  public <T extends TaskRepository> void setRepositories(List<T> repositories) {

    Set<TaskRepository> set = new HashSet<TaskRepository>(myRepositories);
    set.removeAll(repositories);
    myBadRepositories.removeAll(set); // remove all changed reps
    myIssueCache.clear();
    myTemporaryCache.clear();

    myRepositories.clear();
    myRepositories.addAll(repositories);

    reps: for (T repository : repositories) {
      if (repository.isShared() && repository.getUrl() != null) {
        List<TaskProjectConfiguration.SharedServer> servers = getProjectConfiguration().servers;
        TaskRepositoryType type = repository.getRepositoryType();
        for (TaskProjectConfiguration.SharedServer server : servers) {
          if (repository.getUrl().equals(server.url) && type.getName().equals(server.type)) {
            continue reps;
          }
        }
        TaskProjectConfiguration.SharedServer server = new TaskProjectConfiguration.SharedServer();
        server.type = type.getName();
        server.url = repository.getUrl();
        servers.add(server);
      }
    }
  }

  @Override
  public void removeTask(LocalTask task) {
    myTasks.remove(task.getId());
    myContextManager.removeContext(task);
  }

  @Override
  public void addTaskListener(TaskListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void removeTaskListener(TaskListener listener) {
    myDispatcher.removeListener(listener);
  }

  @NotNull
  @Override
  public LocalTask getActiveTask() {
    return myActiveTask;
  }

  @Override
  public List<Task> getIssues(String query) {
    List<Task> tasks = getIssuesFromRepositories(query, 50, 0, true);
    synchronized (myIssueCache) {
      myTemporaryCache.clear();
      myTemporaryCache.putAll(ContainerUtil.assignKeys(tasks.iterator(), KEY_CONVERTOR));
    }
    return tasks;
  }

  @Override
  public List<Task> getCachedIssues() {
    synchronized (myIssueCache) {
      ArrayList<Task> tasks = new ArrayList<Task>(myIssueCache.values());
      tasks.addAll(myTemporaryCache.values());
      return tasks;
    }
  }

  @Nullable
  @Override
  public Task updateIssue(String id) {
    for (TaskRepository repository : getAllRepositories()) {
      if (repository.extractId(id) == null) {
        continue;
      }
      try {
        Task issue = repository.findTask(id);
        if (issue != null) {
          LocalTaskImpl localTask = myTasks.get(id);
          if (localTask != null) {
            localTask.updateFromIssue(issue);
            return localTask;
          }
          return issue;
        }
      }
      catch (Exception e) {
        LOG.info(e);
      }
    }
    return null;
  }

  @Override
  public LocalTaskImpl[] getLocalTasks() {
    synchronized (myTasks) {
      return myTasks.values().toArray(new LocalTaskImpl[myTasks.size()]);
    }
  }

  @Override
  public LocalTaskImpl createLocalTask(String summary) {
    return createTask(LOCAL_TASK_ID_FORMAT.format(myConfig.localTasksCounter++), summary);
  }

  private static LocalTaskImpl createTask(String id, String summary) {
    LocalTaskImpl task = new LocalTaskImpl(id, summary);
    Date date = new Date();
    task.setCreated(date);
    task.setUpdated(date);
    return task;
  }

  @Override
  public void activateTask(@NotNull final Task origin, boolean clearContext, boolean createChangelist) {
    
    myConfig.clearContext = clearContext;

    saveActiveTask();

    if (clearContext) {
      myContextManager.clearContext();
    }
    myContextManager.restoreContext(origin);

    final Task task = doActivate(origin, true);

    if (isVcsEnabled()) {
      List<ChangeListInfo> changeLists = getOpenChangelists(task);
      if (changeLists.isEmpty()) {
        myConfig.createChangelist = createChangelist;
      }

      if (createChangelist) {
        if (changeLists.isEmpty()) {
          String name = TaskUtil.getChangeListName(task);
          String comment = TaskUtil.getChangeListComment(this, origin);
          createChangeList(task, name, comment);
        } else {
          String id = changeLists.get(0).id;
          LocalChangeList changeList = myChangeListManager.getChangeList(id);
          if (changeList != null) {
            myChangeListManager.setDefaultChangeList(changeList);
          }
        }
      }
    }
  }

  private void saveActiveTask() {
    myContextManager.saveContext(myActiveTask);
    myActiveTask.setActive(false);
    myActiveTask.setUpdated(new Date());
  }

  public void createChangeList(Task task, String name) {
    String comment = TaskUtil.getChangeListComment(this, task);
    createChangeList(task, name, comment);
  }

  public void createChangeList(Task task, String name, String comment) {
    LocalChangeList changeList = myChangeListManager.findChangeList(name);
    if (changeList == null) {
      changeList = myChangeListManager.addChangeList(name, comment);
    }
    myChangeListManager.setDefaultChangeList(changeList);
    getOpenChangelists(task).add(new ChangeListInfo(changeList));
  }

  private LocalTask doActivate(Task origin, boolean explicitly) {
    final LocalTaskImpl task = origin instanceof LocalTaskImpl ? (LocalTaskImpl)origin : new LocalTaskImpl(origin);
    if (explicitly) {
      task.setUpdated(new Date());
    }
    task.setActive(true);
    myTasks.put(task.getId(), task);
    if (task.isIssue()) {
      StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new Runnable() {
        public void run() {
          ProgressManager.getInstance().run(new com.intellij.openapi.progress.Task.Backgroundable(myProject, "Updating " + task.getId()) {
            
            public void run(@NotNull ProgressIndicator indicator) {
              updateIssue(task.getId());
            }
          });
        }
      });
    }
    boolean isChanged = !task.equalsTo(myActiveTask);
    myActiveTask = task;
    if (isChanged) {
      myDispatcher.getMulticaster().taskActivated(task);
    }
    return task;
  }

  @Override
  public boolean testConnection(final TaskRepository repository) {

    TestConnectionTask task = new TestConnectionTask("Test connection") {
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setText("Connecting to " + repository.getUrl() + "...");
        indicator.setFraction(0);
        indicator.setIndeterminate(true);
        myConnection = repository.createCancellableConnection();
        if (myConnection != null) {
          Future<Exception> future = ApplicationManager.getApplication().executeOnPooledThread(myConnection);
          while (true) {
            try {
              myException = future.get(100, TimeUnit.MILLISECONDS);
              return;
            }
            catch (TimeoutException ignore) {
              try {
                indicator.checkCanceled();
              }
              catch (ProcessCanceledException e) {
                myException = e;
                myConnection.cancel();
                return;
              }
            }
            catch (Exception e) {
              myException = e;
              return;
            }
          }
        }
        else {
          try {
            repository.testConnection();
          }
          catch (Exception e) {
            LOG.info(e);
            myException = e;
          }
        }
      }
    };
    ProgressManager.getInstance().run(task);
    Exception e = task.myException;
    if (e == null) {
      myBadRepositories.remove(repository);
      Messages.showMessageDialog(myProject, "Connection is successful", "Connection", Messages.getInformationIcon());
    }
    else if (!(e instanceof ProcessCanceledException)) {
      Messages.showErrorDialog(myProject, e.getMessage(), "Error");
    }
    return e == null;
  }

  @SuppressWarnings({"unchecked"})
  public Config getState() {
    myConfig.tasks = ContainerUtil.map(myTasks.values(), new Function<Task, LocalTaskImpl>() {
      public LocalTaskImpl fun(Task task) {
        return new LocalTaskImpl(task);
      }
    });
    myConfig.servers = XmlSerializer.serialize(getAllRepositories());
    return myConfig;
  }

  @SuppressWarnings({"unchecked"})
  public void loadState(Config config) {
    XmlSerializerUtil.copyBean(config, myConfig);
    myTasks.clear();
    for (LocalTaskImpl task : config.tasks) {
      if (!task.isClosed()) {
        myTasks.put(task.getId(), task);
      }
    }

    myRepositories.clear();
    Element element = config.servers;
    List<TaskRepository> repositories = loadRepositories(element);
    myRepositories.addAll(repositories);

    LocalTaskImpl activeTask = null;
    Collections.sort(config.tasks, TASK_UPDATE_COMPARATOR);
    for (LocalTaskImpl task : config.tasks) {
      if (activeTask == null) {
        if (task.isActive()) {
          activeTask = task;
        }
      } else {
        task.setActive(false);
      }
    }

    if (activeTask != null) {
      myActiveTask = activeTask;
    }
  }

  public static ArrayList<TaskRepository> loadRepositories(Element element) {
    ArrayList<TaskRepository> repositories = new ArrayList<TaskRepository>();
    for (TaskRepositoryType repositoryType : ourRepositoryTypes) {
      for (Object o : element.getChildren()) {
        if (((Element)o).getName().equals(repositoryType.getName())) {
          try {
            @SuppressWarnings({"unchecked"})
            TaskRepository repository = (TaskRepository)XmlSerializer.deserialize((Element)o, repositoryType.getRepositoryClass());
            if (repository != null) {
              repository.setRepositoryType(repositoryType);
              repositories.add(repository);
            }
          }
          catch (XmlSerializationException e) {
            // ignore
          }
        }
      }
    }
    return repositories;
  }

  public void projectOpened() {

    TaskProjectConfiguration projectConfiguration = getProjectConfiguration();

    servers: for (TaskProjectConfiguration.SharedServer server : projectConfiguration.servers) {
      if (server.type == null || server.url == null) {
        continue;
      }
      for (TaskRepositoryType<?> repositoryType : ourRepositoryTypes) {
        if (repositoryType.getName().equals(server.type)) {
          for (TaskRepository repository : myRepositories) {
            if (!repositoryType.equals(repository.getRepositoryType())) {
              continue;
            }
            if (server.url.equals(repository.getUrl())) {
              continue servers;
            }
          }
          TaskRepository repository = repositoryType.createRepository();
          repository.setUrl(server.url);
          myRepositories.add(repository);
        }
      }
    }

    myContextManager.pack(200, 50);
  }

  private TaskProjectConfiguration getProjectConfiguration() {
    return ServiceManager.getService(myProject, TaskProjectConfiguration.class);
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "Task Manager";
  }

  public void initComponent() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      myCacheRefreshTimer = new Timer(myConfig.updateInterval*60*1000, new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (myConfig.updateEnabled && !myUpdating) {
            updateIssues(null);
          }
        }
      });
      myCacheRefreshTimer.setInitialDelay(0);

      StartupManager.getInstance(myProject).registerStartupActivity(new Runnable() {
        public void run() {
          myCacheRefreshTimer.start();
        }
      });
    }

    LocalTaskImpl defaultTask = myTasks.get(LocalTaskImpl.DEFAULT_TASK_ID);
    if (defaultTask == null) {
      defaultTask = createDefaultTask();
      myTasks.put(defaultTask.getId(), defaultTask);
    }
    // make sure the task is associated with default changelist
    LocalChangeList defaultList = myChangeListManager.findChangeList(LocalChangeList.DEFAULT_NAME);
    if (defaultList != null) {
      ChangeListInfo listInfo = new ChangeListInfo(defaultList);
      if (!defaultTask.getChangeLists().contains(listInfo)) {
        defaultTask.getChangeLists().add(listInfo);
      }
    }

    // update tasks from change lists
    HashSet<ChangeListInfo> infos = new HashSet<ChangeListInfo>();
    for (LocalTaskImpl task : getLocalTasks()) {
      infos.addAll(task.getChangeLists());
    }
    List<LocalChangeList> changeLists = myChangeListManager.getChangeLists();
    for (LocalChangeList localChangeList : changeLists) {
      ChangeListInfo info = new ChangeListInfo(localChangeList);
      if (!infos.contains(info)) {
        String name = localChangeList.getName();
        String id = extractId(name);
        LocalTask existing = id == null ? myTasks.get(name) : myTasks.get(id);
        if (existing != null) {
          ((LocalTaskImpl)existing).getChangeLists().add(info);
        } else {
          LocalTaskImpl task;
          if (id == null) {
            task = createLocalTask(name);
          }
          else {
            task = createTask(id, name);
            task.setIssue(true);
          }
          task.getChangeLists().add(info);          
          myTasks.put(task.getId(), task);
        }
      }
    }

    doActivate(myActiveTask, false);

    myChangeListManager.addChangeListListener(myChangeListListener);
  }

  private static LocalTaskImpl createDefaultTask() {
    return new LocalTaskImpl(LocalTaskImpl.DEFAULT_TASK_ID, "Default task");
  }

  @Nullable
  private String extractId(String text) {
    for (TaskRepository repository : getAllRepositories()) {
      String id = repository.extractId(text);
      if (id != null) {
        return id;
      }
    }
    return null;
  }

  public void disposeComponent() {
    if (myCacheRefreshTimer != null) {
      myCacheRefreshTimer.stop();
    }
    myChangeListManager.removeChangeListListener(myChangeListListener);
  }

  public void updateIssues(final @Nullable Runnable onComplete) {
    TaskRepository first = ContainerUtil.find(getAllRepositories(), new Condition<TaskRepository>() {
      public boolean value(TaskRepository repository) {
        return repository.isConfigured();
      }
    });
    if (first == null) {
      myIssueCache.clear();
      if (onComplete != null) {
        onComplete.run();
      }
      return;
    }
    myUpdating = true;
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        try {
          List<Task> issues = getIssuesFromRepositories(null, myConfig.updateIssuesCount, 0, false);

          synchronized (myIssueCache) {
            myIssueCache.clear();
            myIssueCache.putAll(ContainerUtil.assignKeys(issues.iterator(), KEY_CONVERTOR));
          }
          // update local tasks
           synchronized (myTasks) {
             for (Iterator<Map.Entry<String,LocalTaskImpl>> it = myTasks.entrySet().iterator(); it.hasNext();) {
               Map.Entry<String,LocalTaskImpl> entry = it.next();
               Task issue = myIssueCache.get(entry.getKey());
               if (issue != null) {
                 if (issue.isClosed()) {
                   it.remove();
                 } else {
                   entry.getValue().updateFromIssue(issue);
                 }
               }
             }
           }
        }
        finally {
          if (onComplete != null) {
            onComplete.run();
          }
          myUpdating = false;
        }
      }
    });
  }

  private List<Task> getIssuesFromRepositories(String request, int max, long since, boolean forceRequest) {
    List<Task> issues = new ArrayList<Task>();
    for (final TaskRepository repository : getAllRepositories()) {
      if (!repository.isConfigured() || (!forceRequest && myBadRepositories.contains(repository))) {
        continue;
      }
      try {
        Task[] tasks = repository.getIssues(request, max, since);
        myBadRepositories.remove(repository);
        ContainerUtil.addAll(issues, tasks);
      }
      catch (Exception e) {
        myBadRepositories.add(repository);
        LOG.warn(e);
        Notifications.Bus.register(TASKS_NOTIFICATION_GROUP, NotificationDisplayType.BALLOON);
        Notifications.Bus.notify(new Notification(TASKS_NOTIFICATION_GROUP, "Cannot connect to " + repository.getUrl(),
                                                  "<p><a href=\"\">Configure server...</a></p>", NotificationType.WARNING,
                                                  new NotificationListener() {
                                                    public void hyperlinkUpdate(@NotNull Notification notification,
                                                                                @NotNull HyperlinkEvent event) {
                                                      TaskRepositoriesConfigurable configurable =
                                                        new TaskRepositoriesConfigurable(myProject);
                                                      ShowSettingsUtil.getInstance().editConfigurable(myProject, configurable);
                                                      if (!ArrayUtil.contains(repository, getAllRepositories())) {
                                                        notification.expire();
                                                      }
                                                    }
                                                  }), myProject);
      }
    }
    return issues;
  }

  @Override
  public boolean isVcsEnabled() {
    return ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss().length > 0;
  }

  @Nullable
  @Override
  public LocalTask getAssociatedTask(LocalChangeList list) {
    for (LocalTaskImpl task : getLocalTasks()) {
      if (list.getId().equals(task.getAssociatedChangelistId())) {
        return task;
      }
    }
    return null;
  }

  @Override
  public void associateWithTask(LocalChangeList changeList) {
    String id = changeList.getId();
    for (LocalTaskImpl localTask : getLocalTasks()) {
      if (localTask.getAssociatedChangelistId() == null) {
        for (ChangeListInfo info : localTask.getChangeLists()) {
          if (id.equals(info.id)) {
            localTask.setAssociatedChangelistId(id);
            return;
          }
        }
      }
    }
    String comment = changeList.getComment();
    LocalTaskImpl task = createLocalTask(StringUtil.isEmpty(comment) ? changeList.getName() : comment);
    task.getChangeLists().add(new ChangeListInfo(changeList));
    task.setAssociatedChangelistId(id);
  }

  @NotNull
  @Override
  public List<ChangeListInfo> getOpenChangelists(Task task) {
    if (task instanceof LocalTaskImpl) {
      List<ChangeListInfo> changeLists = ((LocalTaskImpl)task).getChangeLists();
      for (Iterator<ChangeListInfo> it = changeLists.iterator(); it.hasNext();) {
        ChangeListInfo changeList = it.next();
        if (myChangeListManager.getChangeList(changeList.id) == null) {
          it.remove();
        }
      }
      return changeLists;
    }
    else {
      return Collections.emptyList();
    }
  }

  public void decorateChangeList(LocalChangeList changeList, ColoredTreeCellRenderer cellRenderer, boolean selected,
                                 boolean expanded, boolean hasFocus) {
    LocalTask task = getAssociatedTask(changeList);
    if (task == null) {
      task = findTaskByChangelist(changeList);
    }
    if (task != null && task.isIssue()) {
      cellRenderer.setIcon(task.getIcon());
    }
  }

  public static class Config {

    @Property(surroundWithTag = false)
    @AbstractCollection(surroundWithTag = false, elementTag="task")
    public List<LocalTaskImpl> tasks = new ArrayList<LocalTaskImpl>();

    public int localTasksCounter = 1;

    public int taskHistoryLength = 50;

    public boolean updateEnabled = true;
    public int updateInterval = 5;
    public int updateIssuesCount = 50;

    public boolean clearContext = true;
    public boolean createChangelist = true;
    public boolean trackContextForNewChangelist = true;
    public boolean markAsInProgress = false;

    @Tag("servers")
    public Element servers = new Element("servers");
  }

  private abstract class TestConnectionTask extends com.intellij.openapi.progress.Task.Modal {

    protected Exception myException;

    @Nullable
    protected TaskRepository.CancellableConnection myConnection;

    public TestConnectionTask(String title) {
      super(TaskManagerImpl.this.myProject, title, true);
    }

    @Override
    public void onCancel() {
      if (myConnection != null) {
        myConnection.cancel();
      }
    }
  }

}
