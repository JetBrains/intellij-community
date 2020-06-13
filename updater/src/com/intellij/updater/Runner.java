// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.updater;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.NOPLogger;
import org.apache.log4j.spi.NOPLoggerRepository;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class Runner {
  private static final String PATCH_FILE_NAME = "patch-file.zip";
  private static final String ERROR_LOG_FILE_NAME = "idea_updater_error.log";  // must be equal to UpdateCheckerComponent.ERROR_LOG_FILE_NAME

  private static Logger logger = null;
  private static String logPath = null;
  private static boolean ourCaseSensitiveFs;

  public static Logger logger() {
    return logger;
  }

  public static boolean isCaseSensitiveFs() {
    return ourCaseSensitiveFs;
  }

  public static void main(String[] args) {
    initLogger();
    try {
      _main(args);
    }
    catch (Throwable t) {
      logger().error("internal error", t);
      System.exit(2);
    }
  }

  private static void initLogger() {
    String logDirectory = Utils.findDirectory(1_000_000L);
    logPath = new File(logDirectory, "idea_updater.log").getAbsolutePath();

    FileAppender update = new FileAppender();
    update.setFile(logPath);
    update.setLayout(new PatternLayout("%d{dd/MM HH:mm:ss} %-5p %C{1}.%M - %m%n"));
    update.setThreshold(Level.ALL);
    update.setAppend(true);
    update.activateOptions();

    FileAppender updateError = new FileAppender();
    updateError.setFile(new File(logDirectory, ERROR_LOG_FILE_NAME).getAbsolutePath());
    updateError.setLayout(new PatternLayout("%d{dd/MM HH:mm:ss} %-5p %C{1}.%M - %m%n"));
    updateError.setThreshold(Level.ERROR);
    updateError.setAppend(false);
    updateError.activateOptions();

    logger = Logger.getLogger("com.intellij.updater");
    logger.addAppender(updateError);
    logger.addAppender(update);
    logger.setLevel(Level.ALL);
    logger.info("--- Updater started ---");
  }

  private static void _main(String[] args) {
    String jarFile = getArgument(args, "jar");
    if (jarFile == null) {
      jarFile = resolveJarFile();
    }

    if (args.length >= 6 && "create".equals(args[0])) {
      String oldVersionDesc = args[1];
      String newVersionDesc = args[2];
      String oldFolder = args[3];
      String newFolder = args[4];
      String patchFile = args[5];

      checkCaseSensitivity(newFolder);

      boolean binary = hasArgument(args, "zip_as_binary");
      boolean strict = hasArgument(args, "strict");
      boolean normalized = hasArgument(args, "normalized");

      String root = getArgument(args, "root");
      if (root == null) {
        root = "";
      }
      else if (!root.endsWith("/")) {
        root += "/";
      }

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

      boolean success = create(spec);
      System.exit(success ? 0 : 1);
    }
    else if (args.length >= 2 && ("install".equals(args[0]) || "apply".equals(args[0])) ||
             args.length >= 3 && ("batch-install".equals(args[0]))) {
      String destPath = args[1];

      Path destDirectory = Paths.get(destPath);
      try {
        destDirectory = destDirectory.toRealPath();
      }
      catch (InvalidPathException | IOException e) {
        logger().error(e);
      }

      checkCaseSensitivity(destDirectory.toString());

      logger().info("args: " + Arrays.toString(args));
      logger().info("destination: " + destPath + " (" + destDirectory + "), case-sensitive: " + ourCaseSensitiveFs);

      UpdaterUI ui;
      if ("install".equals(args[0]) || "batch-install".equals(args[0])) {
        ui = new SwingUpdaterUI();
      }
      else if (hasArgument(args, "toolbox-ui")) {
        ui = new ToolboxUpdaterUI();
      }
      else {
        ui = new ConsoleUpdaterUI();
      }

      boolean backup = !hasArgument(args, "no-backup");
      boolean success;
      if (!Files.isDirectory(destDirectory, LinkOption.NOFOLLOW_LINKS)) {
        ui.showError("Invalid target directory: " + destPath);
        success = false;
      }
      else if (!"batch-install".equals(args[0])) {
        success = install(jarFile, destDirectory, ui, backup);
      }
      else {
        String[] patches = args[2].split(File.pathSeparator);
        success = install(patches, destDirectory, ui, backup);
      }
      System.exit(success ? 0 : 1);
    }
    else {
      printUsage();
    }
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

  private static boolean hasArgument(String[] args, String name) {
    return Arrays.asList(args).contains("--" + name);
  }

  public static void checkCaseSensitivity(String path) {
    boolean orig = new File(path).exists();
    ourCaseSensitiveFs = orig != new File(path.toUpperCase(Locale.ENGLISH)).exists() ||
                         orig != new File(path.toLowerCase(Locale.ENGLISH)).exists();
  }

  private static Map<String, String> buildWarningMap(List<String> warnings) {
    Map<String, String> map = new HashMap<>();
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

  public static List<String> extractArguments(String[] args, String paramName) {
    List<String> result = new ArrayList<>();
    String prefix = paramName + '=';
    for (String param : args) {
      if (param.startsWith(prefix)) {
        StringTokenizer tokenizer = new StringTokenizer(param.substring(prefix.length()), ";");
        while (tokenizer.hasMoreTokens()) {
          result.add(tokenizer.nextToken());
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
      "  <patch_file>: The .jar patch file to create, which will contain the patch and the patcher.\n" +
      "  <file_set>: Can be one of:\n" +
      "    ignored: The set of files that will not be included in the patch.\n" +
      "    critical: Fully included in the patch, so they can be replaced at the destination even if they have changed.\n" +
      "    optional: A set of files that is okay for them not to exist when applying the patch.\n" +
      "    delete: A set of regular expressions for paths that is safe to delete without user confirmation.\n" +
      "  <flags>: Can be:\n" +
      "    --zip_as_binary: Zip and jar files will be treated as binary files and not inspected internally.\n" +
      "    --strict: The created patch will contain extra information to fully validate an installation. A strict\n" +
      "              patch will only be applied if it is guaranteed that the patched version will match exactly\n" +
      "              the source of the patch. This means that unexpected files will be deleted and all existing files\n" +
      "              will be validated\n" +
      "    --root=<dir>: Sets dir as the root directory of the patch. The root directory is the directory where the patch should be\n" +
      "                  applied to. For example on Mac, you can diff the two .app folders and set Contents as the root.\n" +
      "                  The root directory is relative to <old_folder> and uses forwards-slashes as separators.\n" +
      "    --normalized: This creates a normalized patch. This flag only makes sense in addition to --zip_as_binary\n" +
      "                  A normalized patch must be used to move from an installation that was patched\n" +
      "                  in a non-binary way to a fully binary patch. This will yield a larger patch, but\n" +
      "                  the generated patch can be applied on versions where non-binary patches have been applied to and it\n" +
      "                  guarantees that the patched version will match exactly the original one.\n" +
      "  <folder>: The folder where product was installed. For example: c:/Program Files/JetBrains/IntelliJ IDEA 2017.3.4");
  }

  private static boolean create(PatchSpec spec) {
    ConsoleUpdaterUI ui = new ConsoleUpdaterUI();
    boolean success = false;

    try {
      File tempPatchFile = Utils.getTempFile("patch");
      PatchFileCreator.create(spec, tempPatchFile, ui);

      logger().info("Packing JAR file: " + spec.getPatchFile() );
      ui.startProcess("Packing JAR file '" + spec.getPatchFile() + "'...");

      try (ZipOutputWrapper out = new ZipOutputWrapper(new FileOutputStream(spec.getPatchFile()));
           ZipInputStream in = new ZipInputStream(new FileInputStream(new File(spec.getJarFile())))) {
        ZipEntry e;
        while ((e = in.getNextEntry()) != null) {
          out.zipEntry(e, in);
        }

        out.zipFile(PATCH_FILE_NAME, tempPatchFile);
        out.finish();
      }

      success = true;
    }
    catch (Throwable t) {
      logger().error("create failed", t);
      ui.showError(printStackTrace(t));
    }
    finally {
      try {
        cleanup(ui);
      }
      catch (Throwable t) {
        success = false;
        logger().error("cleanup failed", t);
        ui.showError(printStackTrace(t));
      }
    }

    return success;
  }

  private static String printStackTrace(Throwable t) {
    StringWriter writer = new StringWriter();
    t.printStackTrace(new PrintWriter(writer));
    return writer.toString();
  }

  private static void cleanup(UpdaterUI ui) throws IOException {
    logger().info("Cleaning up...");
    ui.startProcess("Cleaning up...");
    ui.setProgressIndeterminate();
    Utils.cleanup();
  }

  private static boolean install(String patch, Path dest, UpdaterUI ui, boolean doBackup) {
    try {
      PatchFileCreator.PreparationResult preparationResult;
      File backupDir = null;
      PatchFileCreator.ApplicationResult applicationResult;

      try {
        File patchFile = Utils.getTempFile("patch");

        logger().info("Extracting patch file...");
        ui.startProcess("Extracting patch file...");
        ui.setProgressIndeterminate();
        try (ZipFile zipFile = new ZipFile(patch);
             InputStream in = Utils.getEntryInputStream(zipFile, PATCH_FILE_NAME);
             OutputStream out = new BufferedOutputStream(new FileOutputStream(patchFile))) {
          Utils.copyStream(in, out);
        }

        ui.checkCancelled();

        File destDir = dest.toFile();
        preparationResult = PatchFileCreator.prepareAndValidate(patchFile, destDir, ui);

        Map<String, ValidationResult.Option> resolutions = askForResolutions(preparationResult.validationResults, ui);

        if (doBackup) {
          backupDir = Utils.getTempFile("backup");
          if (!backupDir.mkdir()) throw new IOException("Cannot create a backup directory: " + backupDir);
        }

        applicationResult = PatchFileCreator.apply(preparationResult, resolutions, backupDir, ui);
      }
      catch (OperationCancelledException e) {
        logger().warn("cancelled", e);
        return false;
      }
      catch (Throwable t) {
        logger().error("prepare failed", t);
        String message = "An error occurred when preparing the patch:\n" +
                         t.getClass().getSimpleName() + ": " + t.getMessage() + "\n\n" +
                         ui.bold("No files were changed. Please retry applying the patch.") + "\n\n" +
                         "More details in the log: " + logPath;
        ui.showError(message);
        return false;
      }

      if (!applicationResult.applied) {
        List<PatchAction> appliedActions = applicationResult.appliedActions;
        Throwable error = applicationResult.error;

        if (error != null) {
          String message = "An error occurred when applying the patch:\n" +
                           error.getClass().getSimpleName() + ": " + error.getMessage() + "\n\n";
          if (appliedActions.isEmpty()) {
            message += ui.bold("No files were changed. Please retry applying the patch.");
          }
          else if (backupDir == null) {
            message += ui.bold("Files may be corrupted. Please reinstall the IDE.");
          }
          else {
            message += ui.bold("Files may be corrupted. The patch will attempt to revert the changes.");
          }
          message += "\n\nMore details in the log: " + logPath;
          ui.showError(message);
        }

        if (!appliedActions.isEmpty() && backupDir != null) {
          try {
            PatchFileCreator.revert(preparationResult, appliedActions, backupDir, ui);
          }
          catch (Throwable t) {
            logger().error("revert failed", t);
            String message = "An error occurred when reverting the patch:\n" +
                             t.getClass().getSimpleName() + ": " + t.getMessage() + "\n\n" +
                             ui.bold("Files may be corrupted. Please reinstall the IDE.") + "\n\n" +
                             "More details in the log: " + logPath;
            ui.showError(message);
          }
        }
      }

      return applicationResult.applied;
    }
    finally {
      try {
        cleanup(ui);
        refreshApplicationIcon(dest.toString());
      }
      catch (Throwable t) {
        logger().warn("cleanup failed", t);
      }
    }
  }

  private static boolean install(String[] patches, Path dest, UpdaterUI ui, boolean backup) {
    try {
      List<File> patchFiles = new ArrayList<>(patches.length);
      File destDir = dest.toFile();
      File backupDir = null;

      String jarName = null;
      try {
        logger().info("Extracting patch files...");
        ui.startProcess("Extracting patch files...");
        for (int i = 0; i < patches.length; i++) {
          jarName = new File(patches[i]).getName();
          logger().info("Unpacking " + jarName);
          ui.setProgress(100 * i / patches.length);
          File patchFile = Utils.getTempFile("patch" + i);
          patchFiles.add(patchFile);
          try (ZipFile zipFile = new ZipFile(patches[i]);
               InputStream in = Utils.getEntryInputStream(zipFile, PATCH_FILE_NAME);
               OutputStream out = new BufferedOutputStream(new FileOutputStream(patchFile))) {
            Utils.copyStream(in, out);
          }
          ui.checkCancelled();
        }
        jarName = null;

        if (backup) {
          backupDir = Utils.getTempFile("backup");
          if (!backupDir.mkdir()) throw new IOException("Cannot create a backup directory: " + backupDir);

          logger().info("Backing up files...");
          ui.startProcess("Backing up files...");
          ui.setProgressIndeterminate();
          Utils.copyDirectory(destDir.toPath(), backupDir.toPath());
        }
      }
      catch (OperationCancelledException e) {
        logger().warn("cancelled", e);
        return false;
      }
      catch (Throwable t) {
        logger().error("prepare failed", t);
        String message = "An error occurred when " + (jarName != null ? "extracting " + jarName : "preparing the patch") + ":\n" +
                         t.getClass().getSimpleName() + ": " + t.getMessage() + "\n\n" +
                         ui.bold("No files were changed. Please retry applying the patch.") + "\n\n" +
                         "More details in the log: " + logPath;
        ui.showError(message);
        return false;
      }

      boolean completed = false, needRestore = false;
      Throwable error = null;
      try {
        for (File patchFile : patchFiles) {
          PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(patchFile, destDir, ui);

          Map<String, ValidationResult.Option> resolutions = askForResolutions(preparationResult.validationResults, ui);

          PatchFileCreator.ApplicationResult applicationResult = PatchFileCreator.apply(preparationResult, resolutions, null, ui);
          needRestore |= !applicationResult.appliedActions.isEmpty();
          if (!applicationResult.applied) {
            error = applicationResult.error;
            throw new OperationCancelledException();
          }
        }
        completed = true;
      }
      catch (OperationCancelledException e) {
        logger().warn("cancelled", e);
      }
      catch (Throwable t) {
        logger().error("batch failed", t);
        error = t;
      }

      if (error != null) {
        String message = "An error occurred when applying the patch:\n" +
                         error.getClass().getSimpleName() + ": " + error.getMessage() + "\n\n";
        if (!needRestore) {
          message += ui.bold("No files were changed. Please retry applying the patches.");
        }
        else if (backupDir == null) {
          message += ui.bold("Files may be corrupted. Please reinstall the IDE.");
        }
        else {
          message += ui.bold("Files may be corrupted. The patch will attempt to revert the changes.");
        }
        message += "\n\nMore details in the log: " + logPath;
        ui.showError(message);
      }

      ui.setDescription("");

      if (!completed && needRestore && backupDir != null) {
        logger().info("Reverting...");
        ui.startProcess("Reverting...");
        ui.setProgressIndeterminate();

        try {
          Utils.delete(destDir);
          try {
            logger().info("move: " + backupDir + " -> " + destDir);
            Files.move(backupDir.toPath(), destDir.toPath());
          }
          catch (IOException e) {
            logger().error("move failed", e);
            Utils.delete(destDir);
            Utils.copyDirectory(backupDir.toPath(), destDir.toPath());
          }
        }
        catch (Throwable t) {
          logger().error("revert failed", t);
          String message = "An error occurred when reverting the patch:\n" +
                           t.getClass().getSimpleName() + ": " + t.getMessage() + "\n\n" +
                           ui.bold("Files may be corrupted. Please reinstall the IDE.") + "\n\n" +
                           "More details in the log: " + logPath;
          ui.showError(message);
        }
      }

      return completed;
    }
    finally {
      try {
        cleanup(ui);
        refreshApplicationIcon(dest.toString());
      }
      catch (Throwable t) {
        logger().warn("cleanup failed", t);
      }
    }
  }

  private static Map<String, ValidationResult.Option> askForResolutions(
    List<ValidationResult> problems, UpdaterUI ui
  ) throws OperationCancelledException {
    if (problems.isEmpty()) return Collections.emptyMap();
    logger().warn("conflicts:");
    for (ValidationResult problem : problems) {
      logger().warn("  " + problem.action.name() + " @ " + problem.path + ": " + problem.message);
    }
    Map<String, ValidationResult.Option> resolutions = ui.askUser(problems);
    logger().warn("resolutions:");
    for (Map.Entry<String, ValidationResult.Option> entry : resolutions.entrySet()) {
      logger().warn("  " + entry.getKey() + ": " + entry.getValue());
    }
    return resolutions;
  }

  private static void refreshApplicationIcon(String destPath) {
    if (Utils.IS_MAC) {
      try {
        String applicationPath = destPath.contains("/Contents") ? destPath.substring(0, destPath.lastIndexOf("/Contents")) : destPath;
        logger().info("refreshApplicationIcon for: " + applicationPath);
        Runtime runtime = Runtime.getRuntime();
        String[] args = {"touch", applicationPath};
        runtime.exec(args);
      }
      catch (IOException e) {
        logger().warn("refreshApplicationIcon failed", e);
      }
    }
  }

  private static String resolveJarFile() {
    URL url = Runner.class.getResource("");
    if (url == null) throw new IllegalArgumentException("Cannot resolve JAR file path");
    if (!"jar".equals(url.getProtocol())) throw new IllegalArgumentException("Patch file is not a JAR file");

    String path = url.getPath();

    int start = path.indexOf("file:/");
    int end = path.indexOf("!/");
    if (start == -1 || end == -1) throw new IllegalArgumentException("Unknown protocol: " + url);

    String jarFileUrl = path.substring(start, end);

    try {
      return new File(new URI(jarFileUrl)).getAbsolutePath();
    }
    catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

  static void initTestLogger() {
    if (logger == null) {
      logger = new NOPLogger(new NOPLoggerRepository(), "root");
    }
    else if (!(logger instanceof NOPLogger)) {
      throw new IllegalStateException("Non-test logger already defined");
    }
  }
}