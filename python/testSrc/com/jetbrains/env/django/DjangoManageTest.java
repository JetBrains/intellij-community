package com.jetbrains.env.django;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.jetbrains.django.manage.BaseCommand;
import com.jetbrains.django.manage.ManageTasksModel;
import com.jetbrains.django.util.ManagePyUtil;
import com.jetbrains.env.python.debug.PyEnvTestCase;
import com.jetbrains.python.PythonTestUtil;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Set;

/**
 * User : ktisha
 */
public class DjangoManageTest extends PyEnvTestCase {

  public void testTaskListCompleteness() throws IOException {
    runPythonTest(new DjangoManageTestTask() {
      @Override
      protected String getTestDataPath() {
        return PythonTestUtil.getTestDataPath() + "/django/manage/completeness";
      }

      public void testing() throws Exception {
        waitForOutput("Process finished with exit code");

        Set<String> defCommands = getCommands();
        String allCommands = ToStringBuilder.reflectionToString(defCommands.toArray(), ToStringStyle.SIMPLE_STYLE);
        for (BaseCommand task : ManageTasksModel.getTasks(null)) {
          assertTrue(String.format("'%s' not found in a list of possible commands :%s", task.getCommand(), allCommands),
                     defCommands.remove(task.getCommand()));
        }
        defCommands.remove("startproject");
        assertEmpty(
          "Not all possible commands was listed:" + ToStringBuilder.reflectionToString(defCommands.toArray(), ToStringStyle.SIMPLE_STYLE),
          defCommands);
      }

      public Set<String> getCommands() throws ExecutionException {
        Set<String> result = ManagePyUtil.extractAvailableCommands(stdout() + stderr());
        assertFalse(stdout() + stderr(), result.isEmpty());
        result.add("createsuperuser");
        result.add("changepassword");

        return result;
      }

      @Nullable
      @Override
      public ConfigurationFactory getFactory() {
        return null;
      }

      @Override
      protected String getSubcommand() {
        return "";
      }
    });
  }
}
