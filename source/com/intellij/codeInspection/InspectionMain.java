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
import com.intellij.ide.license.AuthorizationAction;
import com.intellij.ide.license.impl.InspectionLicense;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.licensecommon.license.LicenseData;

public class InspectionMain {
  public static void main(String[] args) {
    PluginManager.main(args, InspectionMain.class.getName(), "start");
  }

  protected static void start(final String[] args) {
    if (!com.intellij.idea.Main.checkStartupPossible()) {
      System.exit(-1);
    }
    InspectionLicense.getInstance().startUp(new AuthorizationAction() {
      public void proceed(LicenseData license) {
        if (args.length < 3) {
          printHelp();
        }

        System.setProperty("idea.load.plugins.category", "inspection");
        final InspectionApplication application = new InspectionApplication();

        application.myProjectPath = args[0];
        application.myProfilePath = args[1];
        application.myOutPath = args[2];

        try {
          for (int i = 3; i < args.length; i++) {
            String arg = args[i];
            if ("-d".equals(arg)) {
              application.mySourceDirectory = args[++i];
            }
            else if ("-v0".equals(arg)) {
              application.setVerboseLevel(0);
            }
            else if ("-v1".equals(arg)) {
              application.setVerboseLevel(1);
            }
            else if ("-v2".equals(arg)) {
              application.setVerboseLevel(2);
            }
            else {
              printHelp();
            }
          }
        }
        catch (ArrayIndexOutOfBoundsException e) {
          printHelp();
        }

        application.startup();
      }

      public void cancel() {
      }
    });
  }


  public static void printHelp() {
    System.out.println("Expected parameters: <project_file_path> <inspection_profile_file_path> <output_path> [<options>]\n" +
                       "Avaliable options are:\n" +
                       "-d <directory_path>  --  directory to be inspected. Optional. Whole project is inspected by default.\n" +
                       "-v[0|1|2]            --  verbose level. 0 - silent, 1 - verbose, 2 - most verbose.");
    System.exit(1);
  }
}

