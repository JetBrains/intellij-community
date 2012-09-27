package com.intellij.tasks.mantis;

import biz.futureware.mantis.rpc.soap.client.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.actions.TaskSearchSupport;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Nullable;

import javax.xml.rpc.ServiceException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
@Tag("Mantis")
public class MantisRepository extends BaseRepositoryImpl {
  private final static Logger LOG = Logger.getInstance("#com.intellij.tasks.mantis.MantisRepository");

  private final static String SOAP_API_LOCATION = "/api/soap/mantisconnect.php";

  private Map<MantisProject, List<MantisFilter>> myProject2FiltersCachedData;

  private MantisProject myProject;
  private MantisFilter myFilter;

  @SuppressWarnings({"UnusedDeclaration"})
  public MantisRepository() {
  }

  public MantisRepository(TaskRepositoryType type) {
    super(type);
  }

  private MantisRepository(MantisRepository other) {
    super(other);
    myProject = other.getProject();
    myFilter = other.getFilter();
  }

  @Override
  public BaseRepository clone() {
    return new MantisRepository(this);
  }

  @Override
  public Task[] getIssues(String request, int max, long since) throws Exception {
    MantisConnectPortType soap = createSoap();
    try {
      IssueData[] issues;
      if (MantisFilter.LAST_TASKS.equals(myFilter)) {
        issues = soap.mc_project_get_issues(getUsername(), getPassword(), BigInteger.valueOf(myProject.getId()), BigInteger.ZERO,
                                            BigInteger.valueOf(max));
      }
      else {
        issues = soap.mc_filter_get_issues(getUsername(), getPassword(), BigInteger.valueOf(myProject.getId()),
                                           BigInteger.valueOf(myFilter.getId()), BigInteger.ZERO, BigInteger.valueOf(max));
      }
      final List<Task> filteredTasks =
        TaskSearchSupport
          .filterTasks(request == null ? "" : request, ContainerUtil.mapNotNull(issues, new NullableFunction<IssueData, Task>() {
            public Task fun(IssueData issueData) {
              try {
                return createIssue(issueData);
              }
              catch (Exception e) {
                return null;
              }
            }
          }));
      return filteredTasks.toArray(new Task[filteredTasks.size()]);
    }
    catch (Exception e) {
      IssueHeaderData[] issues;
      if (MantisFilter.LAST_TASKS.equals(myFilter)) {
        issues = soap.mc_project_get_issue_headers(getUsername(), getPassword(), BigInteger.valueOf(myProject.getId()), BigInteger.ZERO,
                                                   BigInteger.valueOf(max));
      }
      else {
        issues = soap.mc_filter_get_issue_headers(getUsername(), getPassword(), BigInteger.valueOf(myProject.getId()),
                                                  BigInteger.valueOf(myFilter.getId()), BigInteger.ZERO, BigInteger.valueOf(max));
      }
      final List<Task> filteredTasks =
        TaskSearchSupport
          .filterTasks(request == null ? "" : request, ContainerUtil.mapNotNull(issues, new NullableFunction<IssueHeaderData, Task>() {
            public Task fun(IssueHeaderData issueHeaderData) {
              try {
                return createIssue(issueHeaderData);
              }
              catch (Exception e) {
                return null;
              }
            }
          }));
      return filteredTasks.toArray(new Task[filteredTasks.size()]);
    }
  }

  @Override
  public Task findTask(String id) throws Exception {
    IssueData data = createSoap().mc_issue_get(getUsername(), getPassword(), BigInteger.valueOf(Integer.valueOf(id)));
    return data == null ? null : createIssue(data);
  }

  @Nullable
  @Override
  public CancellableConnection createCancellableConnection() {
    return new CancellableConnection() {
      @Override
      protected void doTest() throws Exception {
        refreshProjectAndFiltersData();
      }

      @Override
      public void cancel() {
      }
    };
  }

  @Nullable
  private Task createIssue(final IssueData data) throws Exception {
    String id = String.valueOf(data.getId());
    if (id == null) return null;
    String summary = data.getSummary();
    if (summary == null) return null;
    LocalTaskImpl task = new MantisTask(id, summary, myProject, this) {
      @Override
      public String getDescription() {
        return data.getDescription();
      }
    };

    task.setUpdated(data.getLast_updated().getTime());
    task.setCreated(data.getDate_submitted().getTime());
    return task;
  }

  @Nullable
  private Task createIssue(final IssueHeaderData data) throws Exception {
    String id = String.valueOf(data.getId());
    if (id == null) return null;
    String summary = data.getSummary();
    if (summary == null) return null;
    LocalTaskImpl task = new MantisTask(id, summary, myProject, this);

    task.setIssue(true);
    task.setUpdated(data.getLast_updated().getTime());
    return task;
  }

  public Set<MantisProject> getProjects() throws Exception {
    if (myProject2FiltersCachedData == null) {
      refreshProjectAndFiltersData();
    }
    return myProject2FiltersCachedData.keySet();
  }

  public List<MantisFilter> getFilters(MantisProject project) throws Exception {
    if (myProject2FiltersCachedData == null) {
      refreshProjectAndFiltersData();
    }
    return myProject2FiltersCachedData.get(project);
  }

  public void refreshProjectAndFiltersData() throws Exception {
    final MantisConnectPortType soap = createSoap();
    myProject2FiltersCachedData = new HashMap<MantisProject, List<MantisFilter>>();
    ProjectData[] projectDatas = soap.mc_projects_get_user_accessible(getUsername(), getPassword());
    List<MantisProject> projects = ContainerUtil.map(projectDatas, new Function<ProjectData, MantisProject>() {
      @Override
      public MantisProject fun(final ProjectData data) {
        return new MantisProject(data.getId().intValue(), data.getName());
      }
    });
    projects.add(MantisProject.ALL_PROJECTS);
    for (MantisProject project : projects) {
      FilterData[] filterDatas = soap.mc_filter_get(getUsername(), getPassword(), BigInteger.valueOf(project.getId()));
      List<MantisFilter> filters = new ArrayList<MantisFilter>();
      String version = soap.mc_version();
      if (!MantisProject.ALL_PROJECTS.equals(project) || !version.startsWith("1.1")) {
        filters.add(MantisFilter.LAST_TASKS);
      }
      filters.addAll(ContainerUtil.map(filterDatas, new Function<FilterData, MantisFilter>() {
        @Override
        public MantisFilter fun(final FilterData data) {
          return new MantisFilter(data.getId().intValue(), data.getName());
        }
      }));
      myProject2FiltersCachedData.put(project, filters);
    }
  }

  private synchronized MantisConnectPortType createSoap() throws ServiceException, MalformedURLException {
    return new MantisConnectLocator().getMantisConnectPort(new URL(getUrl() + SOAP_API_LOCATION));
  }

  public MantisProject getProject() {
    return myProject;
  }

  public void setProject(@Nullable final MantisProject project) {
    myProject = project;
  }

  public MantisFilter getFilter() {
    return myFilter;
  }

  public void setFilter(@Nullable final MantisFilter filter) {
    myFilter = filter;
  }

  @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
  @Override
  public boolean equals(Object o) {
    return super.equals(o) &&
           Comparing.equal(getProject(), ((MantisRepository)o).getProject()) &&
           Comparing.equal(getFilter(), ((MantisRepository)o).getFilter());
  }
}
