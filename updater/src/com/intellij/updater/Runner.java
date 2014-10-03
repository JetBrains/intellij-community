package com.intellij.updater;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import javax.swing.*;
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
  private static final String PATCH_PROPERTIES_ENTRY = "patch.properties";
  private static final String OLD_BUILD_DESCRIPTION = "old.build.description";
  private static final String NEW_BUILD_DESCRIPTION = "new.build.description";

  public static void main(String[] args) throws Exception {
    if (args.length >= 6 && "create".equals(args[0])) {
      String oldVersionDesc = args[1];
      String newVersionDesc = args[2];
      String oldFolder = args[3];
      String newFolder = args[4];
      String patchFile = args[5];
      initLogger();

      List<String> ignoredFiles = extractFiles(args, "ignored");
      List<String> criticalFiles = extractFiles(args, "critical");
      List<String> optionalFiles = extractFiles(args, "optional");
      create(oldVersionDesc, newVersionDesc, oldFolder, newFolder, patchFile, ignoredFiles, criticalFiles, optionalFiles);
    }
    else if (args.length >= 2 && "install".equals(args[0])) {
      // install [--exit0] <destination_folder>
      int nextArg = 1;

      // Default install exit code is SwingUpdaterUI.RESULT_REQUIRES_RESTART (42) unless overridden to be 0.
      // This is used by testUI/build.gradle as gradle expects a javaexec to exit with code 0.
      boolean useExitCode0 = false;
      if (args[nextArg].equals("--exit0")) {
        useExitCode0 = true;
        nextArg++;
      }

      String destFolder = args[nextArg++];
      initLogger();
      logger.info("destFolder: " + destFolder);

      install(useExitCode0, destFolder);
    }
    else {
      printUsage();
    }
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

  public static void infoStackTrace(String msg, Throwable e){
    logger.info(msg, e);
  }

  public static void printStackTrace(Throwable e){
    logger.error(e.getMessage(), e);
  }

  public static List<String> extractFiles(String[] args, String paramName) {
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
    System.err.println("Usage:\n" +
                       "create <old_version_description> <new_version_description> <old_version_folder> <new_version_folder>" +
                       " <patch_file_name> <log_directory> [ignored=file1;file2;...] [critical=file1;file2;...] [optional=file1;file2;...]\n" +
                       "install [--exit0] <destination_folder> [log_directory]\n");
  }

  private static void create(String oldBuildDesc,
                             String newBuildDesc,
                             String oldFolder,
                             String newFolder,
                             String patchFile,
                             List<String> ignoredFiles,
                             List<String> criticalFiles,
                             List<String> optionalFiles) throws IOException, OperationCancelledException {
    File tempPatchFile = Utils.createTempFile();
    createImpl(oldBuildDesc,
               newBuildDesc,
               oldFolder,
               newFolder,
               patchFile,
               tempPatchFile,
               ignoredFiles,
               criticalFiles,
               optionalFiles,
               new ConsoleUpdaterUI(), resolveJarFile());
  }

  static void createImpl(String oldBuildDesc,
                         String newBuildDesc,
                         String oldFolder,
                         String newFolder,
                         String outPatchJar,
                         File   tempPatchFile,
                         List<String> ignoredFiles,
                         List<String> criticalFiles,
                         List<String> optionalFiles,
                         UpdaterUI ui,
                         File resolvedJar) throws IOException, OperationCancelledException {
    try {
      PatchFileCreator.create(new File(oldFolder),
                              new File(newFolder),
                              tempPatchFile,
                              ignoredFiles,
                              criticalFiles,
                              optionalFiles,
                              ui);

      logger.info("Packing JAR file: " + outPatchJar );
      ui.startProcess("Packing JAR file '" + outPatchJar + "'...");

      FileOutputStream fileOut = new FileOutputStream(outPatchJar);
      try {
        ZipOutputWrapper out = new ZipOutputWrapper(fileOut);
        ZipInputStream in = new ZipInputStream(new FileInputStream(resolvedJar));
        try {
          ZipEntry e;
          while ((e = in.getNextEntry()) != null) {
            out.zipEntry(e, in);
          }
        }
        finally {
          in.close();
        }

        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        try {
          Properties props = new Properties();
          props.setProperty(OLD_BUILD_DESCRIPTION, oldBuildDesc);
          props.setProperty(NEW_BUILD_DESCRIPTION, newBuildDesc);
          props.store(byteOut, "");
        }
        finally {
          byteOut.close();
        }

        out.zipBytes(PATCH_PROPERTIES_ENTRY, byteOut);
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

  private static void install(final boolean useExitCode0, final String destFolder) throws Exception {
    InputStream in = Runner.class.getResourceAsStream("/" + PATCH_PROPERTIES_ENTRY);
    Properties props = new Properties();
    try {
      props.load(in);
    }
    finally {
      in.close();
    }

    // todo[r.sh] to delete in IDEA 14 (after a full circle of platform updates)
    if (System.getProperty("swing.defaultlaf") == null) {
      SwingUtilities.invokeAndWait(new Runnable() {
        @Override
        public void run() {
          try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
          }
          catch (Exception ignore) {
            printStackTrace(ignore);
          }
        }
      });
    }

    new SwingUpdaterUI(props.getProperty(OLD_BUILD_DESCRIPTION),
                  props.getProperty(NEW_BUILD_DESCRIPTION),
                  useExitCode0 ? 0 : SwingUpdaterUI.RESULT_REQUIRES_RESTART,
                  new SwingUpdaterUI.InstallOperation() {
                    @Override
                    public boolean execute(UpdaterUI ui) throws OperationCancelledException {
                      logger.info("installing patch to the " + destFolder);
                      return doInstall(ui, destFolder);
                    }
                  });
  }

  interface IJarResolver {
    File resolveJar() throws IOException;
  }

  private static boolean doInstall(UpdaterUI ui, String destFolder) throws OperationCancelledException {
    return doInstallImpl(ui, destFolder, new IJarResolver() {
      @Override
      public File resolveJar() throws IOException {
        return resolveJarFile();
      }
    });
  }

  static boolean doInstallImpl(UpdaterUI ui,
                               String destFolder,
                               IJarResolver jarResolver) throws OperationCancelledException {
    try {
      try {
        File patchFile = Utils.createTempFile();
        ZipFile jarFile = new ZipFile(jarResolver.resolveJar());

        logger.info("Extracting patch file...");
        ui.startProcess("Extracting patch file...");
        ui.setProgressIndeterminate();
        try {
          InputStream in = Utils.getEntryInputStream(jarFile, PATCH_FILE_NAME);
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
          jarFile.close();
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
        cleanup(ui);
      }
      catch (IOException e) {
        ui.showError(e);
        printStackTrace(e);
      }
    }

    return false;
  }

  private static File resolveJarFile() throws IOException {
    URL url = Runner.class.getResource("");
    if (url == null) throw new IOException("Cannot resolve JAR file path");
    if (!"jar".equals(url.getProtocol())) throw new IOException("Patch file is not a JAR file");

    String path = url.getPath();

    int start = path.indexOf("file:/");
    int end = path.indexOf("!/");
    if (start == -1 || end == -1) throw new IOException("Unknown protocol: " + url);

    String jarFileUrl = path.substring(start, end);

    try {
      return new File(new URI(jarFileUrl));
    }
    catch (URISyntaxException e) {
      printStackTrace(e);
      throw new IOException(e.getMessage());
    }
  }
}
