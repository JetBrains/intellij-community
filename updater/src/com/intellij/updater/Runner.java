package com.intellij.updater;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class Runner {
  public static Logger logger = null;

  private static final String PATCH_FILE_NAME = "patch-file.zip";

  public static void main(String[] args) throws Exception {

    String jarFile = getArgument(args, "jar");
    jarFile = jarFile == null ? resolveJarFile() : jarFile;

    if (args.length >= 6 && "create".equals(args[0])) {
      String oldVersionDesc = args[1];
      String newVersionDesc = args[2];
      String oldFolder = args[3];
      String newFolder = args[4];
      String patchFile = args[5];
      initLogger();

      // See usage for an explanation of these flags
      boolean binary = Arrays.asList(args).contains("--zip_as_binary");
      boolean strict = Arrays.asList(args).contains("--strict");
      boolean normalized = Arrays.asList(args).contains("--normalized");

      String root = getArgument(args, "root");
      root = root == null ? "" : (root.endsWith("/") ? root : root + "/");

      List<String> ignoredFiles = extractArguments(args, "ignored");
      List<String> criticalFiles = extractArguments(args, "critical");
      List<String> optionalFiles = extractArguments(args, "optional");
      List<String> deleteFiles = extractArguments(args, "delete");
      Map<String, String> warnings = buildWarningMap(extractArguments(args, "warning"));

      PatchSpec spec = new PatchSpec()
        .setOldVersionDescription(oldVersionDesc)
        .setNewVersionDescription(newVersionDesc)
        .setRoot(root)
        .setOldFolder(oldFolder)
        .setNewFolder(newFolder)
        .setPatchFile(patchFile)
        .setJarFile(jarFile)
        .setStrict(strict)
        .setBinary(binary)
        .setNormalized(normalized)
        .setIgnoredFiles(ignoredFiles)
        .setCriticalFiles(criticalFiles)
        .setOptionalFiles(optionalFiles)
        .setDeleteFiles(deleteFiles)
        .setWarnings(warnings);

      create(spec);
    }
    else if (args.length >= 2 && "install".equals(args[0])) {
      String destFolder = args[1];
      initLogger();
      logger.info("destFolder: " + destFolder);

      install(jarFile, destFolder);
    }
    else {
      printUsage();
    }
  }

  private static Map<String, String> buildWarningMap(List<String> warnings) {
    Map<String, String> map = new HashMap<String, String>();
    for (String warning : warnings) {
      int ix = warning.indexOf(":");
      if (ix != -1) {
        String path = warning.substring(0, ix);
        String message = warning.substring(ix + 1).replace("\\n","\n");
        map.put(path, message);
      }
    }
    return map;
  }

  // checks that log directory 1)exists 2)has write perm. and 3)has 1MB+ free space
  private static boolean isValidLogDir(String logFolder) {
    File fileLogDir = new File(logFolder);
    return fileLogDir.isDirectory() && fileLogDir.canWrite() && fileLogDir.getUsableSpace() >= 1000000;
  }

  private static String getLogDir() {
    String logFolder = System.getProperty("idea.updater.log");
    if (logFolder == null || !isValidLogDir(logFolder)) {
      logFolder = System.getProperty("java.io.tmpdir");
      if (!isValidLogDir(logFolder)) {
        logFolder = System.getProperty("user.home");
      }
    }
    return logFolder;
  }

  public static void initLogger() {
    if (logger == null) {
      String logFolder = getLogDir();
      FileAppender update = new FileAppender();

      update.setFile(new File(logFolder, "idea_updater.log").getAbsolutePath());
      update.setLayout(new PatternLayout("%d{dd MMM yyyy HH:mm:ss} %-5p %C{1}.%M - %m%n"));
      update.setThreshold(Level.ALL);
      update.setAppend(true);
      update.activateOptions();

      FileAppender updateError = new FileAppender();
      updateError.setFile(new File(logFolder, "idea_updater_error.log").getAbsolutePath());
      updateError.setLayout(new PatternLayout("%d{dd MMM yyyy HH:mm:ss} %-5p %C{1}.%M - %m%n"));
      updateError.setThreshold(Level.ERROR);
      updateError.setAppend(false);
      updateError.activateOptions();

      logger = Logger.getLogger("com.intellij.updater");
      logger.addAppender(updateError);
      logger.addAppender(update);
      logger.setLevel(Level.ALL);

      logger.info("--- Updater started ---");
    }
  }

  public static void printStackTrace(Throwable e){
    logger.error(e.getMessage(), e);
  }

  public static String getArgument(String[] args, String name) {
    String flag = "--" + name + "=";
    for (String param : args) {
      if (param.startsWith(flag)) {
        return param.substring(flag.length());
      }
    }
    return null;
  }

  public static List<String> extractArguments(String[] args, String paramName) {
    List<String> result = new ArrayList<String>();
    for (String param : args) {
      if (param.startsWith(paramName + "=")) {
        param = param.substring((paramName + "=").length());
        for (StringTokenizer tokenizer = new StringTokenizer(param, ";"); tokenizer.hasMoreTokens();) {
          String each = tokenizer.nextToken();
          result.add(each);
        }
      }
    }
    return result;
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void printUsage() {
    System.err.println(
      "Usage:\n" +
      "  Runner create <old_version> <new_version> <old_folder> <new_folder> <patch_file> [<file_set>=file1;file2;...] [<flags>]\n" +
      "  Runner install <folder>\n" +
      "\n" +
      "Where:\n" +
      "  <old_version>: A description of the version to generate the patch from.\n" +
      "  <new_version>: A description of the version to generate the patch to.\n" +
      "  <old_folder>: The folder where to find the old version.\n" +
      "  <new_folder>: The folder where to find the new version.\n" +
      "  <patch_file>: The .jar patch file to create which contains the patch and the patcher.\n" +
      "  <file_set>: Can be one of:\n" +
      "    ignored: The set of files that will not be included in the patch.\n" +
      "    critical: Fully included in the patch, so they can be replaced at destination even if they have changed.\n" +
      "    optional: A set of files that is ok for them no to exist when applying the patch.\n" +
      "    delete: A set of regular expressions for paths that is safe to delete without user confirmation.\n" +
      "  <flags>: Can be:\n" +
      "    --zip_as_binary: Zip and jar files will be treated as binary files and not inspected internally.\n" +
      "    --strict: The created patch will contain extra information to fully validate an installation. A strict\n" +
      "              patch will only be applied if it is guaranteed that the patched version will match exactly\n" +
      "              the source of the patch. This means that unexpected files will be deleted and all existing files\n" +
      "              will be validated\n" +
      "    --root=<dir>: Sets dir as the root directory of the patch. The root directory is the directory where the patch should be" +
      "                  applied to. For example on Mac, you can diff the two .app folders and set Contents as the root." +
      "                  The root directory is relative to <old_folder> and uses forwards-slashes as separators." +
      "    --normalized: This creates a normalized patch. This flag only makes sense in addition to --zip_as_binary\n" +
      "                  A normalized patch must be used to move from an installation that was patched\n" +
      "                  in a non-binary way to a fully binary patch. This will yield a larger patch, but the\n" +
      "                  generated patch can be applied on versions where non-binary patches have been applied to and it\n" +
      "                  guarantees that the patched version will match exactly the original one.\n");
  }

  private static void create(PatchSpec spec) throws IOException, OperationCancelledException {
    UpdaterUI ui = new ConsoleUpdaterUI();
    try {
      File tempPatchFile = Utils.createTempFile();
      PatchFileCreator.create(spec, tempPatchFile, ui);

      logger.info("Packing JAR file: " + spec.getPatchFile() );
      ui.startProcess("Packing JAR file '" + spec.getPatchFile() + "'...");

      FileOutputStream fileOut = new FileOutputStream(spec.getPatchFile());
      try {
        ZipOutputWrapper out = new ZipOutputWrapper(fileOut);
        ZipInputStream in = new ZipInputStream(new FileInputStream(new File(spec.getJarFile())));
        try {
          ZipEntry e;
          while ((e = in.getNextEntry()) != null) {
            out.zipEntry(e, in);
          }
        }
        finally {
          in.close();
        }

        out.zipFile(PATCH_FILE_NAME, tempPatchFile);
        out.finish();
      }
      finally {
        fileOut.close();
      }
    }
    finally {
      cleanup(ui);
    }
  }

  private static void cleanup(UpdaterUI ui) throws IOException {
    logger.info("Cleaning up...");
    ui.startProcess("Cleaning up...");
    ui.setProgressIndeterminate();
    Utils.cleanup();
  }

  private static void install(final String jarFile, final String destFolder) throws Exception {
    new SwingUpdaterUI(new SwingUpdaterUI.InstallOperation() {
                         public boolean execute(UpdaterUI ui) throws OperationCancelledException {
                           logger.info("installing patch to the " + destFolder);
                           return doInstall(jarFile, ui, destFolder);
                         }
                       });
  }

  private static boolean doInstall(String jarFile, UpdaterUI ui, String destFolder) throws OperationCancelledException {
    try {
      try {
        File patchFile = Utils.createTempFile();
        ZipFile zipFile = new ZipFile(jarFile);

        logger.info("Extracting patch file...");
        ui.startProcess("Extracting patch file...");
        ui.setProgressIndeterminate();
        try {
          InputStream in = Utils.getEntryInputStream(zipFile, PATCH_FILE_NAME);
          OutputStream out = new BufferedOutputStream(new FileOutputStream(patchFile));
          try {
            Utils.copyStream(in, out);
          }
          finally {
            in.close();
            out.close();
          }
        }
        finally {
          zipFile.close();
        }

        ui.checkCancelled();

        File destDir = new File(destFolder);
        PatchFileCreator.PreparationResult result = PatchFileCreator.prepareAndValidate(patchFile, destDir, ui);
        Map<String, ValidationResult.Option> options = ui.askUser(result.validationResults);
        return PatchFileCreator.apply(result, options, ui);
      }
      catch (IOException e) {
        ui.showError(e);
        printStackTrace(e);
      }
    }
    finally {
      try {
        System.gc();
        cleanup(ui);
      }
      catch (IOException e) {
        ui.showError(e);
        printStackTrace(e);
      }
    }

    return false;
  }

  private static String resolveJarFile() throws IOException {
    URL url = Runner.class.getResource("");
    if (url == null) throw new IOException("Cannot resolve JAR file path");
    if (!"jar".equals(url.getProtocol())) throw new IOException("Patch file is not a JAR file");

    String path = url.getPath();

    int start = path.indexOf("file:/");
    int end = path.indexOf("!/");
    if (start == -1 || end == -1) throw new IOException("Unknown protocol: " + url);

    String jarFileUrl = path.substring(start, end);

    try {
      return new File(new URI(jarFileUrl)).getAbsolutePath();
    }
    catch (URISyntaxException e) {
      printStackTrace(e);
      throw new IOException(e.getMessage());
    }
  }
}
