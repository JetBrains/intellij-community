package com.intellij.tasks.mantis;

import com.intellij.openapi.util.Comparing;
import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.actions.TaskSearchSupport;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.tasks.mantis.model.*;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.axis.utils.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.rpc.ServiceException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Dmitry Avdeev
 */
@Tag("Mantis")
public class MantisRepository extends BaseRepositoryImpl {
  private final static String SOAP_API_LOCATION = "/api/soap/mantisconnect.php";

  private List<MantisProject> myProjects;

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

  @Nullable
  @Override
  public String extractId(final String taskName) {
    Matcher matcher = Pattern.compile("\\d+").matcher(taskName);
    return matcher.find() ? matcher.group() : null;
  }

  @Override
  public List<Task> getIssues(@Nullable String request, int max, long since) throws Exception {
    MantisConnectPortType soap = createSoap();
    List<Task> result = new ArrayList<Task>(max);
    int page = 1;
    int issuesOnPage = StringUtils.isEmpty(request) ? max : max * request.length() * 5;
    while (true) {
      final List<Task> issuesFromPage = getIssues(page, issuesOnPage, soap);
      final List<Task> filteredTasks = TaskSearchSupport.filterTasks(request != null ? request : "", issuesFromPage);
      result.addAll(filteredTasks);
      if (issuesFromPage.size() < issuesOnPage || result.size() >= max) {
        break;
      }
      page++;
    }
    result = result.subList(0, Math.min(max, result.size()));
    return result;
  }

  private List<Task> getIssues(final int page, final int issuesOnPage, final MantisConnectPortType soap) throws Exception {
    IssueHeaderData[] issues;
    if (MantisFilter.LAST_TASKS.equals(myFilter)) {
      issues =
        soap.mc_project_get_issue_headers(getUsername(), getPassword(), BigInteger.valueOf(myProject.getId()), BigInteger.valueOf(page),
                                          BigInteger.valueOf(issuesOnPage));
    }
    else {
      issues = soap.mc_filter_get_issue_headers(getUsername(), getPassword(), BigInteger.valueOf(myProject.getId()),
                                                BigInteger.valueOf(myFilter.getId()), BigInteger.valueOf(page),
                                                BigInteger.valueOf(issuesOnPage));
    }
    return ContainerUtil.mapNotNull(issues, new NullableFunction<IssueHeaderData, Task>() {
      public Task fun(IssueHeaderData issueData) {
        return createIssue(issueData);
      }
    });
  }

  @Nullable
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
  private Task createIssue(final IssueData data) {
    String id = String.valueOf(data.getId());
    if (id == null) return null;
    String summary = data.getSummary();
    if (summary == null) return null;
    MantisTask task = new MantisTask(id, summary, myProject, this) {
      @Override
      public String getDescription() {
        return data.getDescription();
      }

      @NotNull
      @Override
      public Comment[] getComments() {
        final List<Comment> comments = ContainerUtil.map(data.getNotes(), new Function<IssueNoteData, Comment>() {
          @Override
          public Comment fun(final IssueNoteData data) {
            return new Comment() {
              @Override
              public String getText() {
                return data.getText();
              }

              @Nullable
              @Override
              public String getAuthor() {
                return data.getReporter().getName();
              }

              @Nullable
              @Override
              public Date getDate() {
                return data.getDate_submitted().getTime();
              }
            };
          }
        });
        return comments.toArray(new Comment[comments.size()]);
      }
    };

    task.setUpdated(data.getLast_updated().getTime());
    task.setCreated(data.getDate_submitted().getTime());
    return task;
  }

  @Nullable
  private Task createIssue(final IssueHeaderData data) {
    String id = String.valueOf(data.getId());
    if (id == null) return null;
    String summary = data.getSummary();
    if (summary == null) return null;
    MantisTask task = new MantisTask(id, summary, myProject, this);

    task.setIssue(true);
    task.setUpdated(data.getLast_updated().getTime());
    return task;
  }

  public List<MantisProject> getProjects() throws Exception {
    if (myProjects == null) {
      refreshProjectAndFiltersData();
    }
    return myProjects;
  }

  public List<MantisFilter> getFilters(MantisProject project) throws Exception {
    if (myProjects == null) {
      refreshProjectAndFiltersData();
    }
    return project.getFilters();
  }

  public void refreshProjectAndFiltersData() throws Exception {
    final MantisConnectPortType soap = createSoap();
    myProjects = new ArrayList<MantisProject>();
    ProjectData[] projectDatas = soap.mc_projects_get_user_accessible(getUsername(), getPassword());
    List<MantisProject> projects = ContainerUtil.map(projectDatas, new Function<ProjectData, MantisProject>() {
      @Override
      public MantisProject fun(final ProjectData data) {
        return new MantisProject(data.getId().intValue(), data.getName());
      }
    });
    projects.add(0, MantisProject.ALL_PROJECTS);
    String version = soap.mc_version();
    for (MantisProject project : projects) {
      FilterData[] filterDatas = soap.mc_filter_get(getUsername(), getPassword(), BigInteger.valueOf(project.getId()));
      List<MantisFilter> filters = new ArrayList<MantisFilter>();
      if (!MantisProject.ALL_PROJECTS.equals(project) || !version.startsWith("1.1")) {
        filters.add(MantisFilter.LAST_TASKS);
      }
      filters.addAll(ContainerUtil.map(filterDatas, new Function<FilterData, MantisFilter>() {
        @Override
        public MantisFilter fun(final FilterData data) {
          return new MantisFilter(data.getId().intValue(), data.getName());
        }
      }));
      project.setFilters(filters);
      myProjects.add(project);
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
