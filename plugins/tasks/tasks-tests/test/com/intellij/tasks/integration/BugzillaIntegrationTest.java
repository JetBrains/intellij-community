package com.intellij.tasks.integration;

import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.bugzilla.BugzillaRepository;
import com.intellij.tasks.bugzilla.BugzillaRepositoryType;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * @author Mikhail Golubev
 */
public class BugzillaIntegrationTest extends TaskManagerTestCase {
  private static final String BUGZILLA_4_TEST_SERVER_URL = "http://trackers-tests.labs.intellij.net:8028/xmlrpc.cgi";
  private static final String BUG_FOR_CUSTOM_STATES_ID = "10";

  private static final CustomTaskState CONFIRMED_STATE = new CustomTaskState("CONFIRMED", "CONFIRMED");
  private static final CustomTaskState IN_PROGRESS_STATE = new CustomTaskState("IN_PROGRESS", "IN_PROGRESS");

  private BugzillaRepository myRepository;

  public void testCustomTaskStates() throws Exception {
    final Task task = myRepository.findTask(BUG_FOR_CUSTOM_STATES_ID);
    assertNotNull(task);
    try {
      assertEquals(TaskState.OPEN, task.getState());

      Set<CustomTaskState> states = myRepository.getAvailableTaskStates(task);
      List<String> stateNames = ContainerUtil.map(states, new Function<CustomTaskState, String>() {
        @Override
        public String fun(CustomTaskState state) {
          return state.getPresentableName();
        }
      });
      assertContainsElements(stateNames, "IN_PROGRESS");
      assertDoesntContain(stateNames, "CONFIRMED");

      // Confirmed -> In Progress
      setStateAndCheckResult(task, IN_PROGRESS_STATE, TaskState.IN_PROGRESS);

      states = myRepository.getAvailableTaskStates(task);
      stateNames = ContainerUtil.map(states, new Function<CustomTaskState, String>() {
        @Override
        public String fun(CustomTaskState state) {
          return state.getPresentableName();
        }
      });
      assertContainsElements(stateNames, "CONFIRMED");
      assertDoesntContain(stateNames, "IN_PROGRESS");
    }
    finally {
      // In Progress -> Confirmed (always attempt to return to original state)
      setStateAndCheckResult(task, CONFIRMED_STATE, TaskState.OPEN);
    }

  }

  private void setStateAndCheckResult(@NotNull Task task, @NotNull CustomTaskState state, @NotNull TaskState expected) throws Exception {
    myRepository.setTaskState(task, state);
    final Task updated = myRepository.findTask(task.getId());
    assertNotNull(updated);
    assertEquals(expected, updated.getState());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRepository = new BugzillaRepository(new BugzillaRepositoryType());
    myRepository.setUrl(BUGZILLA_4_TEST_SERVER_URL);
    myRepository.setUsername("buildtest");
    myRepository.setPassword("buildtest");
  }

}
