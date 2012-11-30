package com.intellij.tasks.integration;

import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.tasks.mantis.MantisFilter;
import com.intellij.tasks.mantis.MantisProject;
import com.intellij.tasks.mantis.MantisRepository;
import com.intellij.tasks.mantis.MantisRepositoryType;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;

/**
 * User: evgeny.zakrevsky
 * Date: 10/12/12
 */
public class MantisIntegrationTest extends TaskManagerTestCase {
  public void testMantis12() throws Exception {
    MantisRepository mantisRepository = new MantisRepository(new MantisRepositoryType());
    mantisRepository.setUrl("http://trackers-tests.labs.intellij.net:8142/");
    mantisRepository.setUsername("deva");
    mantisRepository.setPassword("deva");

    assertTrue(mantisRepository.getProjects().size() >= 2);
    final MantisProject mantisProject = mantisRepository.getProjects().get(1);
    assertEquals(mantisProject.getName(), "Mantis 1.2 project 1");
    mantisRepository.setProject(mantisProject);

    assertTrue(mantisProject.getFilters().size() >= 2);
    MantisFilter mantisFilter = null;
    for (MantisFilter filter : mantisProject.getFilters()) {
      if (filter.getName().equals("Mantis 1.2 Filter 1")) {
        mantisFilter = filter;
      }
    }
    assertNotNull(mantisFilter);
    mantisRepository.setFilter(mantisFilter);

    final Task[] issues = mantisRepository.getIssues("", 1, 0);
    assertTrue(issues.length >= 1);
    final Task task = issues[0];
    assertEquals(task.getId(), "1");
    assertEquals(task.getProject(), "Mantis 1.2 project 1");
    assertEquals(task.getNumber(), "1");
    assertEquals(task.getSummary(), "M12P1I1");

    final Task task1 = mantisRepository.findTask("1");
    assertNotNull(task1);
    assertEquals(task1.getId(), "1");
    assertEquals(task1.getProject(), "Mantis 1.2 project 1");
    assertEquals(task1.getNumber(), "1");
    assertEquals(task1.getSummary(), "M12P1I1");
    assertEquals(task1.getDescription(), ".");

    HttpClient client = new HttpClient();
    final GetMethod method = new GetMethod(task1.getIssueUrl());
    client.executeMethod(method);
    assertEquals(method.getStatusCode(), 200);
  }
}
