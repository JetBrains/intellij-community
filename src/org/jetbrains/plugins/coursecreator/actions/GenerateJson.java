package org.jetbrains.plugins.coursecreator.actions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.coursecreator.CCProjectService;
import org.jetbrains.plugins.coursecreator.format.Course;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class GenerateJson extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(GenerateJson.class.getName());
  public GenerateJson() {
    super("Generate course Json","Generate course Json", null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }

    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = service.getCourse();
    final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();
    final String json = gson.toJson(course);
    final File courseJson = new File(project.getBasePath(), "course.json");
    FileWriter writer = null;
    try {
      writer = new FileWriter(courseJson);
      writer.write(json);
    } catch (IOException e1) {
      LOG.error(e);
    }
    finally {
      try {
        if (writer != null)
          writer.close();
      } catch (IOException e1) {
        LOG.error(e1);
      }
    }
  }

  @Override
  public void update(AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }
    presentation.setVisible(true);
    presentation.setEnabled(true);

  }
}