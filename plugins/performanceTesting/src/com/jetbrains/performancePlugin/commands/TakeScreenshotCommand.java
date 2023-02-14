package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.ui.ImageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TakeScreenshotCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "takeScreenshot";
  private static final Logger LOG = Logger.getInstance(TakeScreenshotCommand.class);

  public TakeScreenshotCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  public @NotNull Promise<Object> _execute(final @NotNull PlaybackContext context) {
    final AsyncPromise<Object> result = new AsyncPromise<>();
    String fullPathToFile = extractCommandArgument(PREFIX);
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      takeScreenshotOfFrame(fullPathToFile);
    });

    result.setResult(null);

    return result;
  }

  public static void takeScreenshotWithAwtRobot(String fullPathToFile) {
    Rectangle rectangle = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
    Robot robot = null;
    try {
      robot = new Robot();
    }
    catch (AWTException e) {
      LOG.info("Exceptions occurs at attempt to create Robot for taking screenshot");
      LOG.info(e);
    }

    assert robot != null;
    BufferedImage img = robot.createScreenCapture(rectangle);
    try {
      File screenshotFile = new File(fullPathToFile);
      ImageIO.write(img, "jpg", screenshotFile);
      if (screenshotFile.exists()) {
        LOG.info("Screenshot saved:" + fullPathToFile);
      }
    }
    catch (IOException e) {
      LOG.info("Exceptions occurs at attempt to write screenshot to file");
      LOG.info(e);
    }
  }

  public static void takeScreenshotOfFrame(String fileName) {
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for(Project project: projects){
      CompletableFuture<Boolean> result = new CompletableFuture<>();
      ApplicationManager.getApplication().invokeLater(()->{
        IdeFrame frame = WindowManager.getInstance().getIdeFrame(project);
        if(frame != null) {
          JComponent component = frame.getComponent();
          BufferedImage img = ImageUtil.createImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
          component.printAll(img.createGraphics());
          String prefix = projects.length == 1 ? "" : project.getName() + "_";
          ApplicationManager.getApplication().executeOnPooledThread(() ->{
            try {
              result.complete(ImageIO.write(img, "png", new File(prefix+fileName)));
            }
            catch (IOException e) {
              LOG.info(e);
            }
          });
        } else {
          LOG.info("Frame was empty when takeScreenshot was called");
        }
      });
      try {
        Boolean fileCreated = result.get(30, TimeUnit.SECONDS);
        if(fileCreated){
          LOG.info("Screenshot is saved at: " + fileName);
        } else {
          LOG.info("No writers are found for screenshot");
        }
      }
      catch (InterruptedException | ExecutionException | TimeoutException e) {
        LOG.info(e);
      }
    }
  }
}