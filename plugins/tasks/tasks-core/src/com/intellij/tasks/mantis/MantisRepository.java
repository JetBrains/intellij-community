package com.intellij.tasks.mantis;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Comparing;
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
import com.intellij.util.text.VersionComparatorUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.axis.AxisFault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
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

  @Override
  public BaseRepository clone() {
    return new MantisRepository(this);
  }

  @Nullable
  @Override
  public String extractId(String taskName) {
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

    List<Task> tasks = new ArrayList<Task>(limit);

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
    List<IssueHeaderData> collectedHeaders = new ArrayList<IssueHeaderData>();
    boolean isWorkaround = myCurrentProject.isUnspecified() && !myAllProjectsAvailable;
    // Projects to iterate over, actually needed only when "All Projects" pseudo-project is selected
    // and is unsupported on server side.
    List<MantisProject> projects = isWorkaround? myProjects : Collections.singletonList(myCurrentProject);
    for (MantisProject project : projects) {
      if (isWorkaround && project.isUnspecified()) {
        continue;
      }
      assert !project.isUnspecified() || myAllProjectsAvailable;
      IssueHeaderData[] headers;
      if (myCurrentFilter.isUnspecified()) {
        headers = soap.mc_project_get_issue_headers(getUsername(), getPassword(),
                                                    bigInteger(project.getId()), bigInteger(pageNumber), bigInteger(pageSize));
      }
      else {
        headers = soap.mc_filter_get_issue_headers(getUsername(), getPassword(),
                                                   bigInteger(project.getId()), bigInteger(myCurrentFilter.getId()),
                                                   bigInteger(pageNumber), bigInteger(pageSize));
      }
      ContainerUtil.addAll(collectedHeaders, headers);
    }
    return ContainerUtil.mapNotNull(collectedHeaders, new NullableFunction<IssueHeaderData, Task>() {
      public Task fun(IssueHeaderData issueData) {
        return createIssue(issueData);
      }
    });
  }

  @Nullable
  @Override
  public Task findTask(String id) throws Exception {
    IssueData data = createSoap().mc_issue_get(getUsername(), getPassword(), bigInteger(Integer.valueOf(id)));
    return data == null ? null : createIssue(data);
  }

  @Nullable
  @Override
  public CancellableConnection createCancellableConnection() {
    return new CancellableConnection() {
      @Override
      protected void doTest() throws Exception {
        try {
          createSoap().mc_enum_access_levels(getUsername(), getPassword());
        }
        catch (AxisFault e) {
          throw new Exception(TaskBundle.message("failure.server.message", e.getMessage()));
        }
      }

      @Override
      public void cancel() {
      }
    };
  }

  @Nullable
  private Task createIssue(IssueData data) {
    if (data.getId() == null || data.getSummary() == null) {
      return null;
    }
    return new MantisTask(data, this);
  }

  @Nullable
  private Task createIssue(IssueHeaderData data) {
    if (data.getId() == null || data.getSummary() == null) {
      return null;
    }
    return new MantisTask(data, myCurrentProject, this);
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

  public void refreshProjects() throws Exception {
    MantisConnectPortType soap = createSoap();
    myAllProjectsAvailable = checkAllProjectsAvailable(soap);

    ProjectData[] projectDatas = soap.mc_projects_get_user_accessible(getUsername(), getPassword());
    List<MantisProject> projects =
      new ArrayList<MantisProject>(ContainerUtil.map(projectDatas, new Function<ProjectData, MantisProject>() {
        @Override
        public MantisProject fun(final ProjectData data) {
          return new MantisProject(data.getId().intValue(), data.getName());
        }
      }));
    List<MantisFilter> commonFilters = new LinkedList<MantisFilter>();
    for (MantisProject project : projects) {
      FilterData[] filterDatas = soap.mc_filter_get(getUsername(), getPassword(), bigInteger(project.getId()));
      List<MantisFilter> projectFilters = new LinkedList<MantisFilter>();
      for (FilterData data : filterDatas) {
        MantisFilter filter = new MantisFilter(data.getId().intValue(), data.getName());
        if (data.getProject_id().intValue() == 0) {
          commonFilters.add(filter);
        }
        projectFilters.add(filter);
      }

      projectFilters.add(0, MantisFilter.newUndefined());
      project.setFilters(projectFilters);
    }

    Collections.sort(commonFilters);
    commonFilters.add(0, MantisFilter.newUndefined());

    MantisProject undefined = MantisProject.newUndefined();
    undefined.setFilters(commonFilters);
    projects.add(0, undefined);

    myProjects = projects;
  }

  @NotNull
  private MantisConnectPortType createSoap() throws Exception {
    return new MantisConnectLocator().getMantisConnectPort(new URL(getUrl() + SOAP_API_LOCATION));
  }

  private static boolean checkAllProjectsAvailable(MantisConnectPortType soap) throws RemoteException {
    // Check whether All Projects is available supported by server
    String version = soap.mc_version();
    return !DEBUG_ALL_PROJECTS && VersionComparatorUtil.compare(version, "1.2.9") >= 0;
  }

  @NotNull
  private static BigInteger bigInteger(int id) {
    return BigInteger.valueOf(id);
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
