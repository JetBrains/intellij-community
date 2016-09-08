package com.studio.updater;

import com.intellij.updater.*;

/**
 * This class performs the install of an intellij patch. Patches are to be created by the {@link Runner} and can then be applied
 * using this program. The arguments for this program follow the same argument structure as the {@link Runner}. The primary difference
 * is the DISPLAY is not required when applying a patch via this class. The intended use of this program is to apply patches where the Java
 * environment argument DISPLAY is set to empty, as it is in either automation, or on a build server.
 *
 * Usage: java --classpath updater.jar com.studio.updater.ConsolePatcher install ../path/to/studio --jar=../path/to/jar.jar
 */
public class ConsolePatcher {

  public static void main(String[] args) throws Exception {
    String jarFile = Runner.getArgument(args, "jar");
    jarFile = jarFile == null ? Runner.resolveJarFile() : jarFile;
    if (args.length >= 2 && "install".equals(args[0])) {
      String destFolder = args[1];
      UpdaterUI ui = new ConsoleUpdaterUI();
      Runner.initLogger();
      Runner.doInstall(jarFile, ui, destFolder);
    }
    else {
      printUsage();
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void printUsage() {
    System.err.println(
      "Usage:\n" +
      "  ConsolePatcher install <folder> --jar=<jar file>\n" +
      "\n" +
      "Where:\n" +
      "  <folder>: The folder where to find the old version.\n" +
      "  --jar=<jar file>: Include the specified patcher code in the generated patch instead of the currently-running");
  }
}
