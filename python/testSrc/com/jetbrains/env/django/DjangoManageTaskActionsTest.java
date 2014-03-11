package com.jetbrains.env.django;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.django.actions.manage.ManageTaskActions;
import com.jetbrains.django.manage.BaseCommand;
import com.jetbrains.django.testRunner.DjangoTestsConfigurationType;
import com.jetbrains.django.util.DjangoUtil;
import com.jetbrains.env.python.debug.PyEnvTestCase;
import com.jetbrains.python.packaging.PyExternalProcessException;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageManagerImpl;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

  /**
   * Fills SDK  roots with installed packages
   *
   * @param sdk package to fill
   * @throws PyExternalProcessException if system was not able to run external tools to list installed packages
   */
  private static void fillSdkWithInstalledPackages(@NotNull final Sdk sdk) throws PyExternalProcessException {
    final List<PyPackage> packages = ((PyPackageManagerImpl)PyPackageManager.getInstance(sdk)).getPackages();
    final Set<String> packagePaths = new HashSet<String>();
    for (final PyPackage aPackage : packages) {
      final String location = aPackage.getLocation();
      if (location != null) {
        packagePaths.add(location);
      }
    }

    final SdkModificator modificator = sdk.getSdkModificator();
    final LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    for (final String path : packagePaths) {
      final VirtualFile virtualFile = fileSystem.findFileByPath(path);
      if (virtualFile == null) {
        throw new IllegalStateException("Failed to read file " + path);
      }
      modificator.addRoot(virtualFile, OrderRootType.CLASSES);
    }


    modificator.commitChanges();
  }

  private static class DjangoManageCommandsTestTask extends DjangoPathTestTask {
    @Nullable
    @Override
    public ConfigurationFactory getFactory() {
      return DjangoTestsConfigurationType.getInstance().getConfigurationFactories()[0];
    }

    @Override
    protected void configure(final AbstractPythonRunConfiguration config) throws IOException {
      // Fill SDK with installed packages
      super.configure(config);
      final Module module = config.getModule();
      assert module != null : "Config has no module";
      try {
        fillSdkWithInstalledPackages(config.getSdk());
      }
      catch (final PyExternalProcessException e) {
        throw new IllegalStateException("Failed to get list of packages", e);
      }
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
