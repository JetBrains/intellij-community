/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 23, 2002
 * Time: 5:20:01 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.InspectionApplication;
import com.intellij.openapi.application.ApplicationStarter;

public class InspectionMain implements ApplicationStarter {
  private InspectionApplication myApplication;

  public String getCommandName() {
    return "inspect";
  }

  public void premain(String[] args) {
    if (args.length < 4) {
      printHelp();
    }

    System.setProperty("idea.load.plugins.category", "inspection");
    myApplication = new InspectionApplication();

    myApplication.myProjectPath = args[1];
    myApplication.myProfilePath = args[2];
    myApplication.myOutPath = args[3];

    try {
      for (int i = 4; i < args.length; i++) {
        String arg = args[i];
        if ("-d".equals(arg)) {
          myApplication.mySourceDirectory = args[++i];
        }
        else if ("-v0".equals(arg)) {
          myApplication.setVerboseLevel(0);
        }
        else if ("-v1".equals(arg)) {
          myApplication.setVerboseLevel(1);
        }
        else if ("-v2".equals(arg)) {
          myApplication.setVerboseLevel(2);
        }
        else {
          printHelp();
        }
      }
    }
    catch (ArrayIndexOutOfBoundsException e) {
      printHelp();
    }
  }

  public void main(String[] args) {
    myApplication.startup();
  }

  public static void printHelp() {
    System.out.println("Expected parameters: <project_file_path> <inspection_profile_file_path> <output_path> [<options>]\n" +
                       "Avaliable options are:\n" +
                       "-d <directory_path>  --  directory to be inspected. Optional. Whole project is inspected by default.\n" +
                       "-v[0|1|2]            --  verbose level. 0 - silent, 1 - verbose, 2 - most verbose.");
    System.exit(1);
  }
}

