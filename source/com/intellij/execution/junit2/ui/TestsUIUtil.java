package com.intellij.execution.junit2.ui;

import com.intellij.execution.Location;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.info.PsiLocator;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.pom.Navigatable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class TestsUIUtil {
  public static final Color PASSED_COLOR = new Color(0, 128, 0);
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.ui.TestsUIUtil");

  private static final String ICONS_ROOT = "/runConfigurations/";

  public static Object getData(final TestProxy testProxy, final String dataId, final JUnitRunningModel model) {
    final Project project = model.getProject();
    if (testProxy == null) return null;
    if (dataId == TestProxy.DATA_CONSTANT) return testProxy;
    if (dataId == DataConstants.NAVIGATABLE) return getOpenFileDescriptor(testProxy, model);
    final PsiLocator testInfo = testProxy.getInfo();
    if (dataId == DataConstants.PSI_ELEMENT) {
      final Location location = testInfo.getLocation(project);
      return location != null ? location.getPsiElement() : null;
    }
    if (dataId == Location.LOCATION) return testInfo.getLocation(project);
    return null;
  }

  public static Navigatable getOpenFileDescriptor(final TestProxy testProxy, final JUnitRunningModel model) {
    final Project project = model.getProject();
    final JUnitConsoleProperties properties = model.getProperties();
    if (testProxy != null) {
      final Location location = testProxy.getInfo().getLocation(project);
      if (JUnitConsoleProperties.OPEN_FAILURE_LINE.value(properties))
        return testProxy.getState().getDescriptor(location);
      else return location != null ? location.getOpenFileDescriptor() : null;
    }
    return null;
  }

  public static Icon loadIcon(final String iconName) {
    final String fullIconName = ICONS_ROOT + iconName +".png";
    final Icon icon = IconLoader.getIcon(fullIconName);
    final Application application = ApplicationManager.getApplication();
    if (application == null || application.isUnitTestMode()) return new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR));
    LOG.assertTrue(icon != null, fullIconName);
    return icon;
  }
}
