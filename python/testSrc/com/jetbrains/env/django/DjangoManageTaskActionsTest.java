package com.jetbrains.env.django;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.jetbrains.django.actions.manage.ManageTaskActions;
import com.jetbrains.django.manage.BaseCommand;
import com.jetbrains.django.testRunner.DjangoTestsConfigurationType;
import com.jetbrains.django.util.DjangoUtil;
import com.jetbrains.env.python.debug.PyEnvTestCase;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * @author Ilya.Kazakevich
 */
public class DjangoManageTaskActionsTest extends PyEnvTestCase {

  /**
   * Checks that "manage.py" UI displays correct commands
   */
  public void testManageCommands() throws Exception {
    runPythonTest(new DjangoManageCommandsTestTask());
  }


  private static class DjangoManageCommandsTestTask extends DjangoPathTestTask {
    @Nullable
    @Override
    public ConfigurationFactory getFactory() {
      return DjangoTestsConfigurationType.getInstance().getConfigurationFactories()[0];
    }


    @Override
    protected void configure(final AbstractPythonRunConfiguration config) throws IOException {
      super.configure(config);
      final Module module = config.getModule();
      assert module != null : "Config has no module";
      ModuleRootModificationUtil.setModuleSdk(module, config.getSdk());


      Assert.assertNotNull("Django not found", DjangoUtil.getDjangoPath(config.getSdk()));

      final Collection<String> expectedCommands = new HashSet<String>();
      expectedCommands.add("runserver");
      expectedCommands.add("dbshell");
      expectedCommands.add("syncdb");
      if (DjangoUtil.isDjangoVersionAtLeast(myFixture.getModule(), 7)) {
        expectedCommands.add("makemigrations");
        expectedCommands.add("migrate");
      }

      final List<BaseCommand> commands = ManageTaskActions.getInstance().getCommands(myFixture.getModule());

      for (final BaseCommand command : commands) {
        expectedCommands.remove(command.getCommand());
      }
      Assert.assertTrue(String.format("Commands '%s' were not found between %s", expectedCommands, commands), expectedCommands.isEmpty());
    }
  }
}
