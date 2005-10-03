/**
 * @author cdr
 */
package com.intellij.ide.startup;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.ZipUtil;
import com.intellij.ide.IdeBundle;

import javax.swing.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NonNls;

public class StartupActionScriptManager {
  @NonNls private static final String ACTION_SCRIPT_FILE = "action.script";

  public static synchronized void executeActionScript() throws IOException {
    List<ActionCommand> commands = loadActionScript();

    for (int i = 0; i < commands.size(); i++) {
      ActionCommand actionCommand = commands.get(i);
      //noinspection HardCodedStringLiteral
      System.out.println("Execute " + actionCommand);
      actionCommand.execute();
    }
    commands.clear();

    saveActionScript(commands);
  }

  public static synchronized void addActionCommand(ActionCommand command) throws IOException {
    final List<ActionCommand> commands = loadActionScript();
    commands.add(command);
    saveActionScript(commands);
  }

  private static String getActionScriptPath() {
    String systemPath = PathManagerEx.getPluginTempPath();
    String filePath = systemPath + File.separator + ACTION_SCRIPT_FILE;
    return filePath;
  }

  private static List<ActionCommand> loadActionScript() throws IOException {
    File file = new File(getActionScriptPath());
    if (file.exists()) {
      ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
      try {
        List<ActionCommand> commands = (List<ActionCommand>)ois.readObject();
        ois.close();

        return commands;
      }
      catch (ClassNotFoundException e) {
        // problem with scrambled code
        // fas fixed, but still appear because corrupted file still exists
        // return empty list.
        System.err.println(IdeBundle.message("error.action.script.corrupted", ApplicationNamesInfo.getInstance().getProductName()));
        return new ArrayList<ActionCommand>();
      }
    }
    else {
      return new ArrayList<ActionCommand>();
    }
  }

  private static void saveActionScript(List<ActionCommand> commands) throws IOException {
    File temp = new File(PathManagerEx.getPluginTempPath());
    if (!temp.exists()) {
      temp.mkdirs();
    }

    File file = new File(getActionScriptPath());
    ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file, false));
    oos.writeObject(commands);
    oos.close();
  }

  private static boolean canCreateFile(File file) {
    if (file.exists()) {
      return file.canWrite();
    }
    else {
      try {
        file.createNewFile();
        file.delete();
        return true;
      }
      catch (IOException e) {
        return false;
      }
    }
  }

  public interface ActionCommand {
    void execute() throws IOException;
  }

  public static class CopyCommand implements Serializable, ActionCommand {
    @NonNls private static final String action = "copy";
    private File mySource;
    private File myDestination;

    public CopyCommand(File source, File destination) {
      myDestination = destination;
      mySource = source;
    }

    public String toString() {
      return action + "[" + mySource.getAbsolutePath() +
             (myDestination == null ? "" : ", " + myDestination.getAbsolutePath()) + "]";
    }

    public void execute() throws IOException {
      // create dirs for destination
      File parentFile = myDestination.getParentFile();
      if (! parentFile.exists())
        if (! myDestination.getParentFile().mkdirs()) {
          JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                        IdeBundle.message("error.cannot.create.plugin.parent.directory", parentFile.getAbsolutePath(),
                                                          myDestination.getAbsolutePath(), parentFile.getParent()), IdeBundle.message("title.installing.plugin"),
                                                                JOptionPane.ERROR_MESSAGE);
        }

      if (!mySource.exists()) {
        //noinspection HardCodedStringLiteral
        System.err.println("Source file " + mySource.getAbsolutePath() + " does not exist for action " + this);
      }
      else if (!canCreateFile(myDestination)) {
        JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                      IdeBundle.message("error.cannot.copy.plugin.file", mySource.getAbsolutePath(),
                                                        myDestination.getAbsolutePath(), myDestination.getParent()),
                                      IdeBundle.message("title.installing.plugin"), JOptionPane.ERROR_MESSAGE);
      }
      else {
        FileUtil.copy(mySource, myDestination);
      }
    }

  }

  public static class UnzipCommand implements Serializable, ActionCommand {
    @NonNls private static final String action = "unzip";
    private File mySource;
    private FilenameFilter myFilenameFilter;
    private File myDestination;

    public UnzipCommand(File source, File destination) {
      this(source, destination, null);
    }

    public UnzipCommand(File source, File destination, FilenameFilter filenameFilter) {
      myDestination = destination;
      mySource = source;
      myFilenameFilter = filenameFilter;
    }

    public String toString() {
      return action + "[" + mySource.getAbsolutePath() +
             (myDestination == null ? "" : ", " + myDestination.getAbsolutePath()) + "]";
    }

    public void execute() throws IOException {
      if (!mySource.exists()) {
        //noinspection HardCodedStringLiteral
        System.err.println("Source file " + mySource.getAbsolutePath() + " does not exist for action " + this);
      }
      else if (!canCreateFile(myDestination)) {
        JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                      IdeBundle.message("error.cannot.unzip.plugin.file", mySource.getAbsolutePath(),
                                                        myDestination.getAbsolutePath(), myDestination),
                                      IdeBundle.message("title.installing.plugin"), JOptionPane.ERROR_MESSAGE);
      }
      else {
        ZipUtil.extract(mySource, myDestination, myFilenameFilter);
      }
    }

  }

  public static class DeleteCommand implements Serializable, ActionCommand {
    @NonNls private static final String action = "delete";
    private File mySource;

    public DeleteCommand(File source) {
      mySource = source;
    }

    public String toString() {
      return action + "[" + mySource.getAbsolutePath() + "]";
    }

    public void execute() throws IOException {
      if (mySource != null && mySource.exists() && !FileUtil.delete(mySource)) {
        //noinspection HardCodedStringLiteral
        System.err.println("Action " + this + " failed.");
        JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                      IdeBundle.message("error.cannot.delete.plugin.file", mySource.getAbsolutePath(),
                                                        mySource.getAbsolutePath()),
                                      IdeBundle.message("title.installing.plugin"), JOptionPane.ERROR_MESSAGE);
      }
    }
  }
}