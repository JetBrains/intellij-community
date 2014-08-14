import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Before;
import org.junit.Test;
import com.jetbrains.python.edu.StudyUtils;
import com.jetbrains.python.edu.course.Course;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.Reader;

import static org.junit.Assert.assertEquals;

/**
 * author: liana
 * data: 7/4/14.
 */
public class JsonParserTest {
  private Course myCourse = null;
  @Before
  public void setUp() throws FileNotFoundException {
    Reader reader = new InputStreamReader(new FileInputStream("EDIDE/testData/course.json"));
    Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    myCourse = gson.fromJson(reader, Course.class);
  }

  @Test
  public void testCourseLevel() {
    assertEquals(myCourse.getName(), "Python для начинающих");
    assertEquals(StudyUtils.getFirst(myCourse.getLessons().get(1).getTaskList().get(0).getUserTests()).getInput(), "sum-input.txt");
    assertEquals(myCourse.getLessons().size(), 2);
    assertEquals(myCourse.getLessons().get(0).getTaskList().size(), 2);
    assertEquals(myCourse.getLessons().get(1).getTaskList().size(), 1);
  }
}
