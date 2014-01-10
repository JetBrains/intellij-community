package com.intellij.updater;

import javax.swing.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.apache.log4j.Logger;
import org.apache.log4j.Level;
import org.apache.log4j.FileAppender;
import org.apache.log4j.PatternLayout;

public class Runner {
  public static Logger logger;
  private static final String PATCH_FILE_NAME = "patch-file.zip";
  private static final String PATCH_PROPERTIES_ENTRY = "patch.properties";
  private static final String OLD_BUILD_DESCRIPTION = "old.build.description";
  private static final String NEW_BUILD_DESCRIPTION = "new.build.description";

  private static void initLogger(){
    String uHome = System.getProperty("user.home");
    System.out.println("***************** --- logConfigure ---");
    System.out.println("***************** uHome: " + uHome);

    FileAppender update = new FileAppender();
    update.setFile(uHome + "/update.log");
    //first version - %d{ABSOLUTE} %5p %t %c{1}:%M:%L - %m%n
    //%-4r [%t] %-5p %c %x - %m%n
    update.setLayout(new PatternLayout("%d{dd MMM yyyy HH:mm:ss} %-5p %C{1}.%M - %m%n"));
    update.setThreshold(Level.TRACE);
    update.setAppend(true);
    update.activateOptions();

    FileAppender update_error = new FileAppender();
    update_error.setFile(uHome + "/update_error.log");
    update_error.setLayout(new PatternLayout("%d{dd MMM yyyy HH:mm:ss} %-5p %C{1}.%M - %m%n"));
    update_error.setThreshold(Level.ERROR);
    update_error.setAppend(true);
    update_error.activateOptions();

    logger = Logger.getLogger("com.intellij.updater");
    logger.addAppender(update_error);
    logger.addAppender(update);
    logger.setLevel(Level.TRACE);
    System.out.println("***************** --- logger created ---");
  }

  public static void main(String[] args) throws Exception {
    initLogger();
    System.out.println("***************** PropertyConfigurator ");
    logger.trace("--- Updater started ---");

    if (args.length != 2 && args.length < 6) {
      printUsage();
      return;
    }

    String command = args[0];
    logger.trace("args[0]: " + args[0]);
    System.out.println("args[0]: " + args[0]);


    if ("create".equals(command)) {
      if (args.length < 6) {
        printUsage();
        return;
      }
      String oldVersionDesc = args[1];
      String newVersionDesc = args[2];
      String oldFolder = args[3];
      String newFolder = args[4];
      String patchFile = args[5];
      List<String> ignoredFiles = extractFiles(args, "ignored");
      List<String> criticalFiles = extractFiles(args, "critical");
      List<String> optionalFiles = extractFiles(args, "optional");
      create(oldVersionDesc, newVersionDesc, oldFolder, newFolder, patchFile, ignoredFiles, criticalFiles, optionalFiles);
    }
    else if ("install".equals(command)) {
      if (args.length != 2) {
        printUsage();
        return;
      }

      String destFolder = args[1];

      logger.trace("args[1]: " + destFolder);
      System.out.println("args[1]: " + destFolder);

      install(destFolder);
    }
    else {
      printUsage();
      return;
    }
  }

  public static List<String> extractFiles(String[] args, String paramName) {
//    logger.trace("List");
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
//    logger.trace("List: " + result.toString());
    logger.trace(result.toString());
    return result;
  }

  private static void printUsage() {
    System.err.println("Usage:\n" +
                       "create <old_version_description> <new_version_description> <old_version_folder> <new_version_folder>" +
                       " <patch_file_name> [ignored=file1;file2;...] [critical=file1;file2;...] [optional=file1;file2;...]\n" +
                       "install <destination_folder>\n");
  }

  private static void create(String oldBuildDesc,
                             String newBuildDesc,
                             String oldFolder,
                             String newFolder,
                             String patchFile,
                             List<String> ignoredFiles,
                             List<String> criticalFiles,
                             List<String> optionalFiles) throws IOException, OperationCancelledException {
    UpdaterUI ui = new ConsoleUpdaterUI();
    try {
      File tempPatchFile = Utils.createTempFile();
      PatchFileCreator.create(new File(oldFolder),
                              new File(newFolder),
                              tempPatchFile,
                              ignoredFiles,
                              criticalFiles,
                              optionalFiles,
                              ui);

      logger.trace("List Packing jar file " + patchFile );
      ui.startProcess("Packing jar file '" + patchFile + "'...");

      FileOutputStream fileOut = new FileOutputStream(patchFile);
      try {
        ZipOutputWrapper out = new ZipOutputWrapper(fileOut);
        ZipInputStream in = new ZipInputStream(new FileInputStream(resolveJarFile()));
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
    catch (Exception ex) {
      logger.error(ex);
    }
    finally {
      cleanup(ui);
    }
  }

  private static void cleanup(UpdaterUI ui) throws IOException {
    logger.trace("Cleaning up...");
    ui.startProcess("Cleaning up...");
    ui.setProgressIndeterminate();
    Utils.cleanup();
  }

  private static void install(final String destFolder) throws Exception {
    System.out.println("install, destFolder: " + destFolder);
    logger.trace("destFolder: " + destFolder);
    InputStream in = Runner.class.getResourceAsStream("/" + PATCH_PROPERTIES_ENTRY);
    Properties props = new Properties();
    try {
      props.load(in);
    }
    catch (Exception ex) {
      logger.error(ex.fillInStackTrace());
    }
    finally {
      in.close();
    }

    SwingUtilities.invokeAndWait(new Runnable() {
      @Override
      public void run() {
        try {
          UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception ex) {
          logger.error(ex.fillInStackTrace());
        }
      }
    });

    new SwingUpdaterUI(props.getProperty(OLD_BUILD_DESCRIPTION),
                  props.getProperty(NEW_BUILD_DESCRIPTION),
                  new SwingUpdaterUI.InstallOperation() {
                    public boolean execute(UpdaterUI ui) throws OperationCancelledException {
                      logger.trace("installing patch to the " + destFolder + " dir");
                      return doInstall(ui, destFolder);
                    }
                  });
  }

  private static boolean doInstall(UpdaterUI ui, String destFolder) throws OperationCancelledException {
    try {
      try {
        File patchFile = Utils.createTempFile();
        ZipFile jarFile = new ZipFile(resolveJarFile());

        logger.trace("Extracting patch file...");
        ui.startProcess("Extracting patch file...");
        ui.setProgressIndeterminate();
        try {
          InputStream in = Utils.getEntryInputStream(jarFile, PATCH_FILE_NAME);
          OutputStream out = new BufferedOutputStream(new FileOutputStream(patchFile));
          try {
            Utils.copyStream(in, out);
          }
          catch (Exception ex) {
            logger.error(ex.fillInStackTrace());
          }

          finally {
            in.close();
            out.close();
          }
        }
        catch (Exception ex) {
          logger.error(ex.fillInStackTrace());
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
        logger.error(e.fillInStackTrace());
      }
    }
    finally {
      try {
        cleanup(ui);
      }
      catch (IOException e) {
        ui.showError(e);
        logger.error(e.fillInStackTrace());
      }
    }

    return false;
  }

  private static File resolveJarFile() throws IOException {
    System.out.println("***************** resolveJarFile");
    URL url = Runner.class.getResource("");
    if (url == null) throw new IOException("Cannot resolve jar file path");
    logger.trace("URL: " + url.getProtocol());
    if (!"jar".equals(url.getProtocol())) throw new IOException("Patch file is not a 'jar' file");

    String path = url.getPath();

    int start = path.indexOf("file:/");
    int end = path.indexOf("!/");
    if (start == -1 || end == -1) throw new IOException("Unknown protocol: " + url);

    String jarFileUrl = path.substring(start, end);

    try {
      return new File(new URI(jarFileUrl));
    }
    catch (URISyntaxException e) {
      logger.error(e.fillInStackTrace());
      throw new IOException(e.getMessage());
    }
  }
}
