package com.intellij.tasks.mantis;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.mantis.model.IssueData;
import com.intellij.tasks.mantis.model.MantisConnectLocator;
import com.intellij.tasks.mantis.model.MantisConnectPortType;
import com.intellij.tasks.mantis.model.ProjectData;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;

import javax.xml.rpc.ServiceException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author Dmitry Avdeev
 */
@Tag("Mantis")
public class MantisRepository extends BaseRepositoryImpl {

  private final static Logger LOG = Logger.getInstance("#com.intellij.tasks.mantis.MantisRepository");

  public MantisRepository() {
  }

  public MantisRepository(TaskRepositoryType type) {
    super(type);
  }

  private MantisRepository(BaseRepository other) {
    super(other);
  }

  @Override
  public BaseRepository clone() {
    return new MantisRepository(this);
  }

  @Override
  public Task[] getIssues(String request, int max, long since) throws Exception {
    MantisConnectPortType soap = createSoap();
    IssueData[] datas =
      soap.mc_filter_get_issues(getUsername(), getPassword(), BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(50));
    return ContainerUtil.map2Array(datas, Task.class, new Function<IssueData, Task>() {
      public Task fun(IssueData issueData) {
        return createIssue(issueData);
      }
    });
  }

  @Override
  public void testConnection() throws Exception {
    MantisConnectPortType soap = createSoap();
    ProjectData[] datas = soap.mc_projects_get_user_accessible(getUsername(), getPassword());
    for (ProjectData data : datas) {
      
    }
  }

  @Override
  public Task findTask(String id) throws Exception {
    IssueData data = createSoap().mc_issue_get(getUsername(), getPassword(), BigInteger.valueOf(0));
    return data == null ? null : createIssue(data);
  }

  private synchronized MantisConnectPortType createSoap() throws ServiceException, MalformedURLException {
//    FileProvider provider = new FileProvider(getClass().getResourceAsStream("/com/intellij/tasks/mantis/client-config.wsdd"));
    MantisConnectLocator locator = new MantisConnectLocator();
    return locator.getMantisConnectPort(new URL(getUrl()));
  }

  private Task createIssue(IssueData data) {
    return new LocalTaskImpl(data.getId().toString(), data.getSummary()) {
      @Override
      public TaskRepository getRepository() {
        return MantisRepository.this;
      }
    };
  }
}
