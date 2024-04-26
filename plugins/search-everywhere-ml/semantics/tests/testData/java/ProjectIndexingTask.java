import java.lang.Thread;

public class ProjectIndexingTask {
  private final String project;

  public ProjectIndexingTask(String project) {
    this.project = project;
  }

  private static String taskTitle = "Collecting project files...";

  public static void startIndexing(long timeout) throws InterruptedException {
    Thread.sleep(timeout);
  }

  private void getUsedMemory() {
    System.out.println("too much");
  }
}