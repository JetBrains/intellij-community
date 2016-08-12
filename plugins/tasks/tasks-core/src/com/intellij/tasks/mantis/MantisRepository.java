package com.intellij.tasks.mantis;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.KeyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.tasks.mantis.model.*;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.proxy.JavaProxyProperty;
import com.intellij.util.text.VersionComparatorUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.axis.AxisFault;
import org.apache.axis.AxisProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.tasks.actions.GotoTaskAction.PAGE_SIZE;

/**
 * @author Dmitry Avdeev
 */
@Tag("Mantis")
public class MantisRepository extends BaseRepositoryImpl {

  private static final boolean DEBUG_ALL_PROJECTS = Boolean.getBoolean("tasks.mantis.debug.all.projects");
  private static final String SOAP_API_LOCATION = "/api/soap/mantisconnect.php";
  private static final Pattern ID_PATTERN = Pattern.compile("\\d+");

  private static final Logger LOG = Logger.getInstance(MantisRepository.class);

  // Projects fetched from server last time is cached, so workaround for IDEA-105413 could work.
  private List<MantisProject> myProjects = null;
  // It means that special pseudo-project "All Projects" is supported on server side.
  // false if Mantis version < 1.2.9, because of http://www.mantisbt.org/bugs/view.php?id=13526
  private boolean myAllProjectsAvailable = true;

  private MantisProject myCurrentProject;
  private MantisFilter myCurrentFilter;

  @SuppressWarnings({"UnusedDeclaration"})
  public MantisRepository() {
    // empty
  }

  public MantisRepository(TaskRepositoryType type) {
    super(type);
  }

  public MantisRepository(MantisRepository other) {
    super(other);
    myCurrentProject = other.getCurrentProject();
    myCurrentFilter = other.getCurrentFilter();
    myProjects = other.myProjects;
    // deep copy isn't needed because, new list will be assigned to field on update in configurable
    myAllProjectsAvailable = other.myAllProjectsAvailable;
  }

  @NotNull
  @Override
  public BaseRepository clone() {
    return new MantisRepository(this);
  }

  @Nullable
  @Override
  public String extractId(@NotNull String taskName) {
    Matcher matcher = ID_PATTERN.matcher(taskName);
    return matcher.find() ? matcher.group() : null;
  }

  @Override
  public Task[] getIssues(@Nullable String query, int offset, int limit, boolean withClosed, @NotNull ProgressIndicator cancelled)
    throws Exception {
    if (myCurrentProject == null || myCurrentFilter == null) {
      throw new Exception(TaskBundle.message("failure.configuration"));
    }
    ensureProjectsRefreshed();
    MantisConnectPortType soap = createSoap();

    List<Task> tasks = new ArrayList<>(limit);

    int pageNumber = offset / PAGE_SIZE + 1;
    // what the heck does it suppose to mean?
    while (tasks.size() < limit) {
      cancelled.checkCanceled();
      int pageSize = Math.min(PAGE_SIZE, limit - tasks.size());
      List<Task> issuesFromPage = getIssuesFromPage(soap, pageNumber, pageSize);
      tasks.addAll(issuesFromPage);
      if (issuesFromPage.size() < pageSize) {
        break;
      }
      pageNumber++;
    }
    return tasks.toArray(new Task[tasks.size()]);
  }

  private List<Task> getIssuesFromPage(@NotNull MantisConnectPortType soap, int pageNumber, int pageSize) throws Exception {
    List<IssueHeaderData> collectedHeaders = new ArrayList<>();
    boolean isWorkaround = myCurrentProject.isUnspecified() && !myAllProjectsAvailable;
    // Projects to iterate over, actually needed only when "All Projects" pseudo-project is selected
    // and is unsupported on server side.
    List<MantisProject> projects = isWorkaround ? myProjects : Collections.singletonList(myCurrentProject);
    for (MantisProject project : projects) {
      if (isWorkaround && project.isUnspecified()) {
        continue;
      }
      assert !project.isUnspecified() || myAllProjectsAvailable;
      IssueHeaderData[] headers = fetchProjectIssues(soap, project, myCurrentFilter, pageNumber, pageSize);
      ContainerUtil.addAll(collectedHeaders, headers);
    }
    return ContainerUtil.mapNotNull(collectedHeaders, (NullableFunction<IssueHeaderData, Task>)issueData -> {
      if (issueData.getId() == null || issueData.getSummary() == null) {
        return null;
      }
      return new MantisTask(issueData, this);
    });
  }

  @Nullable
  @Override
  public Task findTask(@NotNull String id) throws Exception {
    IssueData data = fetchIssueById(createSoap(), id);
    // sanity check
    if (data == null || data.getId() == null || data.getSummary() == null) {
      return null;
    }
    return new MantisTask(data, this);
  }

  @Nullable
  @Override
  public CancellableConnection createCancellableConnection() {
    return new CancellableConnection() {
      @Override
      protected void doTest() throws Exception {
        myProjects = null;
        try {
          refreshProjects();
        }
        catch (Exception e) {
          throw handleException(e);
        }
      }

      @Override
      public void cancel() {
      }
    };
  }

  @NotNull
  public List<MantisProject> getProjects() throws Exception {
    ensureProjectsRefreshed();
    return myProjects == null ? Collections.<MantisProject>emptyList() : myProjects;
  }

  private void ensureProjectsRefreshed() throws Exception {
    if (myProjects == null) {
      refreshProjects();
    }
  }

  void refreshProjects() throws Exception {
    MantisConnectPortType soap = createSoap();
    myAllProjectsAvailable = checkAllProjectsAvailable(soap);

    List<MantisProject> projects =
      new ArrayList<>(ContainerUtil.map(fetchUserProjects(soap), data -> new MantisProject(data)));
    List<MantisFilter> commonFilters = new LinkedList<>();
    for (MantisProject project : projects) {
      FilterData[] rawFilters = fetchProjectFilters(soap, project);
      List<MantisFilter> projectFilters = new LinkedList<>();
      for (FilterData data : rawFilters) {
        MantisFilter filter = new MantisFilter(data);
        if (data.getProject_id().intValue() == 0) {
          commonFilters.add(filter);
        }
        projectFilters.add(filter);
      }

      projectFilters.add(0, MantisFilter.newUndefined());
      project.setFilters(projectFilters);
    }

    Collections.sort(commonFilters, (f1, f2) -> f1.getName().compareTo(f2.getName()));
    commonFilters.add(0, MantisFilter.newUndefined());

    MantisProject undefined = MantisProject.newUndefined();
    undefined.setFilters(commonFilters);
    projects.add(0, undefined);

    myProjects = projects;
  }

  private boolean checkAllProjectsAvailable(MantisConnectPortType soap) throws Exception {
    // Check whether All Projects is available supported by server
    try {
      String version = soap.mc_version();
      boolean available = !DEBUG_ALL_PROJECTS && VersionComparatorUtil.compare(version, "1.2.9") >= 0;
      if (!available) {
        LOG.info("Using Mantis version without 'All Projects' support: " + version);
      }
      return available;
    }
    catch (Exception e) {
      throw handleException(e);
    }
  }

  private Exception handleException(@NotNull Exception e) throws Exception {
    if (e instanceof AxisFault) {
      resetConfiguration();
      throw new Exception(TaskBundle.message("failure.server.message", ((AxisFault)e).getFaultString()), e);
    }
    throw e;
  }

  @NotNull
  private MantisConnectPortType createSoap() throws Exception {
    if (isUseProxy()) {
      for (KeyValue<String, String> pair : HttpConfigurable.getJvmPropertiesList(false, null)) {
        String key = pair.getKey(), value = pair.getValue();
        // Axis uses another names for username and password properties
        // see http://axis.apache.org/axis/java/client-side-axis.html for complete list
        if (key.equals(JavaProxyProperty.HTTP_USERNAME)) {
          AxisProperties.setProperty("http.proxyUser", value);
        }
        else if (key.equals(JavaProxyProperty.HTTP_PASSWORD)) {
          AxisProperties.setProperty("http.proxyPassword", value);
        }
        else {
          AxisProperties.setProperty(key, value);
        }
      }
    }
    return new MantisConnectLocator().getMantisConnectPort(new URL(getUrl() + SOAP_API_LOCATION));
  }

  @Nullable
  private IssueData fetchIssueById(@NotNull MantisConnectPortType soap, @NotNull String id) throws Exception {
    try {
      return soap.mc_issue_get(getUsername(), getPassword(), BigInteger.valueOf(Integer.valueOf(id)));
    }
    catch (RemoteException e) {
      throw handleException(e);
    }
  }

  @NotNull
  private ProjectData[] fetchUserProjects(@NotNull MantisConnectPortType soap) throws Exception {
    try {
      return soap.mc_projects_get_user_accessible(getUsername(), getPassword());
    }
    catch (RemoteException e) {
      throw handleException(e);
    }
  }

  @NotNull
  private FilterData[] fetchProjectFilters(@NotNull MantisConnectPortType soap, @NotNull MantisProject project) throws Exception {
    try {
      return soap.mc_filter_get(getUsername(), getPassword(), BigInteger.valueOf(project.getId()));
    }
    catch (RemoteException e) {
      throw handleException(e);
    }
  }

  @NotNull
  private IssueHeaderData[] fetchProjectIssues(@NotNull MantisConnectPortType soap, @NotNull MantisProject project,
                                               @NotNull MantisFilter filter, int pageNumber, int pageSize) throws Exception {
    try {
      if (filter.isUnspecified()) {
        return soap.mc_project_get_issue_headers(getUsername(), getPassword(),
                                                 BigInteger.valueOf(project.getId()), BigInteger.valueOf(pageNumber),
                                                 BigInteger.valueOf(pageSize));
      }
      else {
        return soap.mc_filter_get_issue_headers(getUsername(), getPassword(),
                                                BigInteger.valueOf(project.getId()), BigInteger.valueOf(filter.getId()),
                                                BigInteger.valueOf(pageNumber), BigInteger.valueOf(pageSize));
      }
    }
    catch (RemoteException e) {
      throw handleException(e);
    }
  }

  private void resetConfiguration() {
    if (myProjects != null) {
      for (MantisProject project : myProjects) {
        if (project.isUnspecified()) {
          myCurrentProject = project;
          for (MantisFilter filter : project.getFilters()) {
            if (filter.isUnspecified()) {
              myCurrentFilter = filter;
              break;
            }
          }
          break;
        }
      }
    } else {
      myCurrentProject = null;
      myCurrentFilter = null;
    }
  }

  @Nullable
  public MantisProject getCurrentProject() {
    return myCurrentProject;
  }

  public void setCurrentProject(@Nullable MantisProject currentProject) {
    myCurrentProject = currentProject;
  }

  @Nullable
  public MantisFilter getCurrentFilter() {
    return myCurrentFilter;
  }

  public void setCurrentFilter(@Nullable MantisFilter currentFilter) {
    myCurrentFilter = currentFilter;
  }

  @Override
  public boolean isConfigured() {
    return super.isConfigured() && StringUtil.isNotEmpty(myUsername) && StringUtil.isNotEmpty(myPassword);
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    MantisRepository repository = (MantisRepository)o;
    if (!Comparing.equal(getCurrentProject(), repository.getCurrentProject())) return false;
    if (!Comparing.equal(getCurrentFilter(), repository.getCurrentFilter())) return false;
    if (!Comparing.equal(myProjects, repository.myProjects)) return false;
    return myAllProjectsAvailable == repository.myAllProjectsAvailable;
  }

  @Override
  protected int getFeatures() {
    return super.getFeatures() & ~NATIVE_SEARCH;
  }
}
