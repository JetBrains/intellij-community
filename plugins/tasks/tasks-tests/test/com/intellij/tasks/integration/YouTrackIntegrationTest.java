package com.intellij.tasks.integration;

import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.tasks.youtrack.YouTrackRepository;
import com.intellij.tasks.youtrack.YouTrackRepositoryType;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;

import java.util.List;
import java.util.Set;

/**
 * @author Mikhail Golubev
 */
public class YouTrackIntegrationTest extends TaskManagerTestCase {
  private static final String YOUTRACK_4_TEST_SERVER_URL = "http://trackers-tests.labs.intellij.net:8067";

  private static final String REQUEST_WITH_CUSTOM_STATES_ID = "YT4CS-1";
  private static final CustomTaskState NORTH_STATE = new CustomTaskState("North", "North");
  private static final CustomTaskState SUBMITTED_STATE = new CustomTaskState("Submitted", "Submitted");

  private YouTrackRepository myRepository;

  public void testCustomTaskStates() throws Exception {
    final Task task = myRepository.findTask(REQUEST_WITH_CUSTOM_STATES_ID);
    assertNotNull(task);

    final Set<CustomTaskState> states = myRepository.getAvailableTaskStates(task);
    final List<String> stateNames = ContainerUtil.map(states, new Function<CustomTaskState, String>() {
      @Override
      public String fun(CustomTaskState state) {
        return state.getPresentableName();
      }
    });
    assertContainsElements(stateNames, "North", "South");

    // ? -> North
    myRepository.setTaskState(task, NORTH_STATE);
    Element element = myRepository.fetchRequestAsElement(REQUEST_WITH_CUSTOM_STATES_ID);
    assertEquals("North", element.getAttributeValue("state"));

    // North -> Submitted
    myRepository.setTaskState(task, SUBMITTED_STATE);
    element = myRepository.fetchRequestAsElement(REQUEST_WITH_CUSTOM_STATES_ID);
    assertEquals("Submitted", element.getAttributeValue("state"));
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRepository = new YouTrackRepository(new YouTrackRepositoryType());
    myRepository.setUrl(YOUTRACK_4_TEST_SERVER_URL);
    myRepository.setUsername("buildtest");
    myRepository.setPassword("buildtest");
  }
}
