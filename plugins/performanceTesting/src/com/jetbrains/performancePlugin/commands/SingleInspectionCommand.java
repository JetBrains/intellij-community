package com.jetbrains.performancePlugin.commands;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.actions.RunInspectionIntention;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.codeInspection.ui.actions.ExportToXMLAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.jetbrains.performancePlugin.PerformanceTestingBundle;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SingleInspectionCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "runSingleInspection";
  private static final Logger LOGGER = Logger.getInstance(SingleInspectionCommand.class);

  public SingleInspectionCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @NotNull
  @Override
  protected Promise<Object> _execute(@NotNull final PlaybackContext context) {
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    final String[] commandArguments = extractCommandArgument(PREFIX).split("\\s+", 2);
    String shortInspectionName = commandArguments[0];
    String extension = "";
    if (commandArguments.length > 1) {
      extension = commandArguments[1];
    }

    @NotNull Project project = context.getProject();
    final InspectionProfile currentProfile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
    final InspectionToolWrapper toolWrapper = currentProfile.getInspectionTool(shortInspectionName, project);
    InspectionManagerEx inspectionManagerEx = (InspectionManagerEx)InspectionManager.getInstance(project);
    if (toolWrapper != null) {
      final InspectionProfileImpl model = RunInspectionIntention.createProfile(toolWrapper, inspectionManagerEx, null);
      //noinspection TestOnlyProblems
      GlobalInspectionContextImpl inspectionContext = new GlobalInspectionContextImpl(project, inspectionManagerEx.getContentManager()) {
        private long numberOfProblems = 0;

        @Override
        protected void notifyInspectionsFinished(@NotNull AnalysisScope scope) {
          super.notifyInspectionsFinished(scope);
          try {
            File tempDirectory = FileUtil.createTempDirectory("inspection", "result");
            final InspectionResultsView view = getView();
            if (view != null) {
              ExportToXMLAction.Companion.dumpToXml(view.getCurrentProfile(), view.getTree(), view.getProject(), view.getGlobalInspectionContext(), tempDirectory.toPath());

              File[] files = tempDirectory.listFiles();
              if (files != null) {
                for (File file : files) {
                  if (file.isHidden()) {
                    continue;
                  }
                  Path path = file.toPath();
                  this.numberOfProblems = Files.lines(path).filter(line -> line.contains("<problem>")).count();
                }
              }
              else {
                LOGGER.error("tempDirectory.listFiles() is null");
              }
            }
          }
          catch (IOException ex) {
            LOGGER.error(ex);
          }
          context.message(PerformanceTestingBundle.message("command.inspection.finish") + " " +
                          PerformanceTestingBundle.message("command.inspection.finish.result", this.numberOfProblems), getLine());
          actionCallback.setDone();
        }
      };
      inspectionContext.setExternalProfile(model);
      AnalysisScope scope = InspectionCommand.getAnalysisScope(extension, project);
      if (scope == null) {
        context.message(PerformanceTestingBundle.message("command.inspection.extension") + " " + extension, getLine());
      }
      else {
        inspectionContext.doInspections(scope);
      }
    }
    else {
      actionCallback.reject(PerformanceTestingBundle.message("command.singleInspection.noinspection", shortInspectionName));
    }


    return Promises.toPromise(actionCallback);
  }
}
