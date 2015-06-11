import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.python.PythonHelpersLocator;
import junit.framework.TestCase;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;


/**
 * author: liana
 * data: 7/4/14.
 */
public class JsonParserTest extends TestCase {
  private Course myCourse = null;

  public void setUp() throws Exception {
    super.setUp();
    Reader reader = new InputStreamReader(new FileInputStream(getTestDataPath() + "/course.json"));
    Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    myCourse = gson.fromJson(reader, Course.class);
  }

  public void testCourseLevel() {
    assertEquals(myCourse.getName(), "Python для начинающих");

    //assertEquals(StudyUtils.getFirst(myCourse.getLessons().get(1).getTaskList().get(0).getUserTests()).getInput(), "sum-input.txt");
    assertEquals(myCourse.getLessons().size(), 2);
    assertEquals(myCourse.getLessons().get(0).getTaskList().size(), 2);
    assertEquals(myCourse.getLessons().get(1).getTaskList().size(), 1);
  }
  protected String getTestDataPath() {
    return PythonHelpersLocator.getPythonCommunityPath() + "/edu/interactive-learning-python/testData";
  }
}
