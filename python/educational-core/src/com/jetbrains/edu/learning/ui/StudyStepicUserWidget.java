package com.jetbrains.edu.learning.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IconLikeCustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.ClickListener;
import com.intellij.ui.HoverHyperlinkLabel;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBUI;
import com.jetbrains.edu.learning.StudySettings;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import com.jetbrains.edu.learning.stepic.StepicUser;
import icons.EducationalCoreIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.MouseEvent;

public class StudyStepicUserWidget implements IconLikeCustomStatusBarWidget {
  public static final String ID = "StepicUser";
  private JLabel myComponent;


  public StudyStepicUserWidget() {
    StepicUser user = StudySettings.getInstance().getUser();
    Icon icon = user == null ? EducationalCoreIcons.StepikOff : EducationalCoreIcons.Stepik;
    myComponent = new JLabel(icon);

    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        Point point = new Point(0, 0);
        StepicUserComponent component = new StepicUserComponent(StudySettings.getInstance().getUser());
        final Dimension dimension = component.getPreferredSize();
        point = new Point(point.x - dimension.width, point.y - dimension.height);
        component.showComponent(new RelativePoint(e.getComponent(), point));
        return true;
      }
    }.installOn(myComponent);
  }

  @NotNull
  @Override
  public String ID() {
    return ID;
  }

  @Nullable
  @Override
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return null;
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
  }

  @Override
  public void dispose() {
    Disposer.dispose(this);
  }

  public void update() {
    StepicUser user = StudySettings.getInstance().getUser();
    Icon icon = user == null ? EducationalCoreIcons.StepikOff : EducationalCoreIcons.Stepik;
    myComponent.setIcon(icon);
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }


  private static class StepicUserComponent extends JPanel {

    private JBPopup myPopup;

    public StepicUserComponent(@Nullable StepicUser user) {
      super();
      BorderLayout layout = new BorderLayout();
      layout.setVgap(10);
      setLayout(layout);

      if (user == null) {
        createUserPanel("You're not logged in", "Log in to Stepik", createAuthorizeUserListener());
      }
      else {
        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        String statusText;
        if (firstName == null || lastName == null || firstName.isEmpty() || lastName.isEmpty()) {
          statusText = "You're logged in";
        }
        else {
          statusText = "<html>You're logged in as <i>" + firstName + " " + lastName + "</i></html>";
        }
        createUserPanel(statusText, "Log out", createLogoutListener());
      }
    }

    @NotNull
    private HyperlinkAdapter createAuthorizeUserListener() {
      return new HyperlinkAdapter() {
        @Override
        protected void hyperlinkActivated(HyperlinkEvent e) {
          EduStepicConnector.doAuthorize(() -> StudyUtils.showOAuthDialog());
          myPopup.cancel();
        }
      };
    }

    @NotNull
    private HyperlinkAdapter createLogoutListener() {
      return new HyperlinkAdapter() {
        @Override
        protected void hyperlinkActivated(HyperlinkEvent e) {
          StudySettings.getInstance().setUser(null);
          myPopup.cancel();
        }
      };
    }

    private void createUserPanel(@NotNull String statusText, @NotNull String actionLabelText, @NotNull HyperlinkAdapter listener) {
      int left_margin = 10;
      int top_margin = 6;
      int action_label_indent = 260;

      JPanel statusPanel = new JPanel(new BorderLayout());
      JLabel statusLabel = new JLabel(statusText);
      statusPanel.add(Box.createVerticalStrut(top_margin), BorderLayout.PAGE_START);
      statusPanel.add(Box.createHorizontalStrut(left_margin), BorderLayout.WEST);
      statusPanel.add(statusLabel, BorderLayout.CENTER);

      JPanel actionPanel = new JPanel(new BorderLayout());
      HoverHyperlinkLabel actionLabel = new HoverHyperlinkLabel(actionLabelText);
      actionLabel.addHyperlinkListener(listener);
      actionPanel.add(Box.createHorizontalStrut(action_label_indent), BorderLayout.LINE_START);
      actionPanel.add(actionLabel, BorderLayout.CENTER);
      actionPanel.add(Box.createHorizontalStrut(left_margin), BorderLayout.EAST);

      JPanel mainPanel = new JPanel();
      BoxLayout layout = new BoxLayout(mainPanel, BoxLayout.PAGE_AXIS);
      mainPanel.setLayout(layout);
      mainPanel.add(statusPanel);
      mainPanel.add(Box.createVerticalStrut(6));
      mainPanel.add(actionPanel);

      add(mainPanel, BorderLayout.PAGE_START);
    }

    @Override
    public Dimension getPreferredSize() {
      final Dimension preferredSize = super.getPreferredSize();
      final int width = JBUI.scale(300);
      if (preferredSize.width < width){
        preferredSize.width = width;
      }
      return preferredSize;
    }

    public void showComponent(RelativePoint point) {
      myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(this, this)
        .setRequestFocus(true)
        .setCancelOnOtherWindowOpen(true)
        .setCancelOnClickOutside(true)
        .setShowBorder(true)
        .createPopup();

      Disposer.register(ApplicationManager.getApplication(), new Disposable() {
        @Override
        public void dispose() {
          Disposer.dispose(myPopup);
        }
      });

      myPopup.show(point);
    }
  }
}
