package com.intellij.tasks.integration;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.tasks.mantis.MantisFilter;
import com.intellij.tasks.mantis.MantisProject;
import com.intellij.tasks.mantis.MantisRepository;
import com.intellij.tasks.mantis.MantisRepositoryType;
import com.intellij.util.xmlb.XmlSerializer;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;

public class MantisIntegrationTest extends TaskManagerTestCase {
  public static final String MANTIS_1_2_11_TEST_SERVER_URL = "http://trackers-tests.labs.intellij.net:8142/";

  private MantisRepository myRepository;

  public void testMantis12() throws Exception {
    assertTrue(myRepository.getProjects().size() >= 2);
    final MantisProject mantisProject = myRepository.getProjects().get(1);
    assertEquals(mantisProject.getName(), "Mantis 1.2 project 1");
    myRepository.setCurrentProject(mantisProject);

    assertTrue(mantisProject.getFilters().size() >= 2);
    MantisFilter mantisFilter = null;
    for (MantisFilter filter : mantisProject.getFilters()) {
      if (filter.getName().equals("Mantis 1.2 Filter 1")) {
        mantisFilter = filter;
      }
    }
    assertNotNull(mantisFilter);
    myRepository.setCurrentFilter(mantisFilter);

    final Task[] issues = myRepository.getIssues("", 0, 1, true, new EmptyProgressIndicator());
    assertTrue(issues.length >= 1);
    final Task task = issues[0];
    assertEquals("1", task.getId());
    // not available here, but is defined in practice, after task has been activated and thus updated from server
    //assertEquals("Mantis 1.2 project 1", task.getProject());
    assertEquals("1", task.getNumber());
    assertEquals("M12P1I1", task.getSummary());

    final Task task1 = myRepository.findTask("1");
    assertNotNull(task1);
    assertEquals("1", task1.getId());
    assertEquals("Mantis 1.2 project 1", task1.getProject());
    assertEquals("1", task1.getNumber());
    assertEquals("M12P1I1", task1.getSummary());
    assertEquals(".", task1.getDescription());

    HttpClient client = new HttpClient();
    final GetMethod method = new GetMethod(task1.getIssueUrl());
    client.executeMethod(method);
    assertEquals(method.getStatusCode(), 200);
  }

  public void testSerialization() {
    MantisRepository repository = new MantisRepository();
    repository.setCurrentProject(new MantisProject());
    repository.setCurrentFilter(new MantisFilter());
    XmlSerializer.serialize(repository);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRepository = new MantisRepository(new MantisRepositoryType());
    myRepository.setUrl(MANTIS_1_2_11_TEST_SERVER_URL);
    myRepository.setUsername("deva");
    myRepository.setPassword("deva");
  }
}
