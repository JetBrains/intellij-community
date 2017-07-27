package training.ui.views;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;
import training.learn.CourseManager;
import training.learn.LearnBundle;
import training.learn.Lesson;
import training.learn.Module;
import training.ui.LearnIcons;
import training.ui.LearnUIManager;
import training.ui.LessonMessagePane;
import training.ui.Message;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseListener;
import java.util.List;

/**
 * @author Sergey Karashevich
 */
public class LearnPanel extends JPanel {

    private final BoxLayout boxLayout;

    //Lesson panel items
    private JPanel lessonPanel;
    private JLabel moduleNameLabel;
    private LinkLabel allTopicsLabel;

    private JLabel lessonNameLabel; //Name of the current lesson
    private LessonMessagePane lessonMessagePane;
    private JPanel buttonPanel;
    private JButton button;


    //Module panel stuff
    private ModulePanel modulePanel;
    private JPanel moduleNamePanel; //contains moduleNameLabel and allTopicsLabel

    //modulePanel UI
    private BoxLayout lessonPanelBoxLayout;

    public LearnPanel() {
        super();
        boxLayout = new BoxLayout(this, BoxLayout.Y_AXIS);
        setLayout(boxLayout);
        setFocusable(false);

        //Obligatory block
        initLessonPanel();
        initModulePanel();

        setOpaque(true);
        setBackground(getBackground());

        lessonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(lessonPanel);
        modulePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        add(modulePanel);

        //set LearnPanel UI
        setPreferredSize(new Dimension(LearnUIManager.getInstance().getWidth(), 100));
        setBorder(LearnUIManager.getInstance().getEmptyBorder());
    }

    private void initLessonPanel() {
        lessonPanel = new JPanel();
        lessonPanelBoxLayout = new BoxLayout(lessonPanel, BoxLayout.Y_AXIS);
        lessonPanel.setLayout(lessonPanelBoxLayout);
        lessonPanel.setFocusable(false);
        lessonPanel.setOpaque(false);

        moduleNameLabel = new JLabel();
        moduleNameLabel.setFont(LearnUIManager.getInstance().getPlainFont());
        moduleNameLabel.setFocusable(false);
        moduleNameLabel.setBorder(LearnUIManager.getInstance().getCheckmarkShiftBorder());

        allTopicsLabel = new LinkLabel(LearnBundle.INSTANCE.message("learn.ui.alltopics"), null);
        allTopicsLabel.setListener((aSource, aLinkData) -> {
            Project guessCurrentProject = ProjectUtil.guessCurrentProject(lessonPanel);
            CourseManager.getInstance().setModulesView();
        }, null);

        lessonNameLabel = new JLabel();
        lessonNameLabel.setBorder(LearnUIManager.getInstance().getCheckmarkShiftBorder());
        lessonNameLabel.setFont(LearnUIManager.getInstance().getLessonHeaderFont());
        lessonNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        lessonNameLabel.setFocusable(false);

        lessonMessagePane = new LessonMessagePane();
        lessonMessagePane.setFocusable(false);
        lessonMessagePane.setOpaque(false);
        lessonMessagePane.setAlignmentX(Component.LEFT_ALIGNMENT);
        lessonMessagePane.setMargin(new Insets(0, 0, 0, 0));
        lessonMessagePane.setBorder(new EmptyBorder(0, 0, 0, 0));
        lessonMessagePane.setMaximumSize(new Dimension(LearnUIManager.getInstance().getWidth(), 10000));


        //Set Next Button UI
        button = new JButton(LearnBundle.INSTANCE.message("learn.ui.button.skip"));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setFocusable(false);
        button.setVisible(true);
        button.setEnabled(true);
        button.setOpaque(false);

        buttonPanel = new JPanel();
        buttonPanel.setBorder(LearnUIManager.getInstance().getCheckmarkShiftBorder());
        buttonPanel.setOpaque(false);
        buttonPanel.setFocusable(false);
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.setAlignmentX(LEFT_ALIGNMENT);
        buttonPanel.add(button);

        lessonPanel.setName("lessonPanel");
        //shift right for checkmark
        lessonPanel.add(moduleNameLabel);
        lessonPanel.add(Box.createVerticalStrut(LearnUIManager.getInstance().getLessonNameGap()));
        lessonPanel.add(lessonNameLabel);
        lessonPanel.add(lessonMessagePane);
        lessonPanel.add(Box.createVerticalStrut(LearnUIManager.getInstance().getBeforeButtonGap()));
        lessonPanel.add(Box.createVerticalGlue());
        lessonPanel.add(buttonPanel);
        lessonPanel.add(Box.createVerticalStrut(LearnUIManager.getInstance().getAfterButtonGap()));
    }


    public void setLessonName(String lessonName) {
        lessonNameLabel.setText(lessonName);
        lessonNameLabel.setForeground(LearnUIManager.getInstance().getDefaultTextColor());
        lessonNameLabel.setFocusable(false);
        this.revalidate();
        this.repaint();
    }

    public void setModuleName(String moduleName) {
        moduleNameLabel.setText(moduleName);
        moduleNameLabel.setForeground(LearnUIManager.getInstance().getDefaultTextColor());
        moduleNameLabel.setFocusable(false);
        this.revalidate();
        this.repaint();
    }


    public void addMessage(String text) {
        lessonMessagePane.addMessage(text);
    }

    public void addMessages(Message[] messages) {

        for (final Message message : messages) {
            if (message.getType() == Message.MessageType.LINK) {
                //add link handler
                message.setRunnable(() -> {
                    final Lesson lesson = CourseManager.getInstance().findLesson(message.getText());
                    if (lesson != null) {
                        try {
                            Project project = ProjectUtil.guessCurrentProject(LearnPanel.this);
                            CourseManager.getInstance().openLesson(project, lesson);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }

        lessonMessagePane.addMessage(messages);
        lessonMessagePane.invalidate();
        lessonMessagePane.repaint();

        //Pack lesson panel
        this.invalidate();
        this.repaint();
        lessonPanel.revalidate();
        lessonPanel.repaint();
        //run to update LessonMessagePane.getMinimumSize and LessonMessagePane.getPreferredSize
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                lessonPanelBoxLayout.invalidateLayout(lessonPanel);
                lessonPanelBoxLayout.layoutContainer(lessonPanel);
            }
        });
    }

    public void setPreviousMessagesPassed() {
        try {
            lessonMessagePane.passPreviousMessages();
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    public void setLessonPassed() {

//        lessonNameLabel.setForeground(lessonPassedColor);
        setButtonToNext();
        this.repaint();
    }

    private void setButtonToNext() {
        button.setVisible(true);
        lessonPanel.revalidate();
        lessonPanel.repaint();
//        button.requestFocus(true); focus requesting is danger here, may interfere with windows like File Structure
    }


    public void hideButtons() {
        if (button.isVisible()) button.setVisible(false);
        this.repaint();
    }

    public void clearLessonPanel() {
//        while (messages.size() > 0){
//            lessonMessageContainer.remove(messages.get(0).getPanel());
//            messages.remove(0);
//        }
//        lessonMessageContainer.removeAll();
        lessonNameLabel.setIcon(null);
        lessonMessagePane.clear();
        //remove links from lessonMessagePane
        final MouseListener[] mouseListeners = lessonMessagePane.getMouseListeners();
        for (MouseListener mouseListener : mouseListeners) {
            lessonMessagePane.removeMouseListener(mouseListener);
        }
//        messages.clear();
        this.revalidate();
        this.repaint();
    }

    public void setButtonNextAction(final Runnable runnable, Lesson notPassedLesson) {
        setButtonNextAction(runnable, notPassedLesson, null);
    }

    public void setButtonNextAction(final Runnable runnable, Lesson notPassedLesson, @Nullable String text) {

        Action buttonAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                runnable.run();
            }
        };
        buttonAction.putValue(Action.NAME, "Next");
        buttonAction.setEnabled(true);
        button.setAction(buttonAction);
        if (notPassedLesson != null) {
            if (text != null) {
                button.setText(text);
            } else {
                button.setText(LearnBundle.INSTANCE.message("learn.ui.button.next.lesson") + ": " + notPassedLesson.getName());
            }
        } else {
            button.setText(LearnBundle.INSTANCE.message("learn.ui.button.next.lesson"));
        }
        button.setSelected(true);
        getRootPane().setDefaultButton(button);
    }

    public void setButtonSkipAction(final Runnable runnable, @Nullable String text, boolean visible) {

        if (getRootPane() != null)
            getRootPane().setDefaultButton(null);
        Action buttonAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                runnable.run();
            }
        };

        buttonAction.setEnabled(true);
        button.setAction(buttonAction);
        if (text == null || text.isEmpty()) button.setText(LearnBundle.INSTANCE.message("learn.ui.button.skip"));
        else button.setText(LearnBundle.INSTANCE.message("learn.ui.button.skip.module") + " " + text);
        button.setSelected(true);
        button.setVisible(visible);
    }


    public void hideNextButton() {
        button.setVisible(false);
    }

    private void initModulePanel() {
        modulePanel = new ModulePanel();
        modulePanel.setLayout(new BoxLayout(modulePanel, BoxLayout.Y_AXIS));
        modulePanel.setFocusable(false);
        modulePanel.setOpaque(false);

        //define separator
        modulePanel.setBorder(new MatteBorder(1, 0, 0, 0, LearnUIManager.getInstance().getSeparatorColor()));
    }

    public ModulePanel getModulePanel() {
        return modulePanel;
    }

    public void clear() {
        clearLessonPanel();
        //clearModulePanel
        modulePanel.removeAll();
    }

    public void updateButtonUi() {
        button.updateUI();
    }

    public class ModulePanel extends JPanel {
        private BidirectionalMap<Lesson, MyLinkLabel> lessonLabelMap = new BidirectionalMap<Lesson, MyLinkLabel>();

        public void init(Lesson lesson) {

            initModuleLessons(lesson);
        }

        private void initModuleLessons(final Lesson lesson) {
            if (lesson == null) return;
            if (lesson.getModule() == null) return;
            Module module = lesson.getModule();
            final List<Lesson> myLessons = module.getLessons();

            //if module contains one lesson only
            if (myLessons.size() == 1) return;

            //create ModuleLessons region
            JLabel moduleLessons = new JLabel();

            moduleNamePanel = new JPanel();
            moduleNamePanel.setBorder(new EmptyBorder(LearnUIManager.getInstance().getLessonGap(), LearnUIManager.getInstance().getCheckIndent(), 0, 0));
            moduleNamePanel.setOpaque(false);
            moduleNamePanel.setFocusable(false);
            moduleNamePanel.setLayout(new BoxLayout(moduleNamePanel, BoxLayout.X_AXIS));
            moduleNamePanel.setAlignmentX(LEFT_ALIGNMENT);
            moduleNamePanel.add(moduleLessons);
            moduleNamePanel.add(Box.createHorizontalStrut(20));
            moduleNamePanel.add(Box.createHorizontalGlue());
            moduleNamePanel.add(allTopicsLabel);

            moduleLessons.setText(lesson.getModule().getName());
            moduleLessons.setFont(LearnUIManager.getInstance().getBoldFont());
            moduleLessons.setFocusable(false);

            add(Box.createRigidArea(new Dimension(0, 5)));
            add(moduleNamePanel);
            add(Box.createRigidArea(new Dimension(0, 10)));

            buildLessonLabels(lesson, myLessons);
            setMaximumSize(new Dimension(LearnUIManager.getInstance().getWidth(), modulePanel.getPreferredSize().height));
        }

        private void buildLessonLabels(Lesson lesson, final List<Lesson> myLessons) {
            lessonLabelMap.clear();
            for (final Lesson currentLesson : myLessons) {
                String lessonName = currentLesson.getName();

                final MyLinkLabel e = new MyLinkLabel(lessonName);
                e.setHorizontalTextPosition(SwingConstants.LEFT);
                e.setBorder(new EmptyBorder(0, LearnUIManager.getInstance().getCheckIndent(), LearnUIManager.getInstance().getLessonGap(), 0));
                e.setFocusable(false);
                e.setListener((aSource, aLinkData) -> {
                    try {
                        Project project = ProjectUtil.guessCurrentProject(LearnPanel.this);
                        CourseManager.getInstance().openLesson(project, currentLesson);
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                }, null);

                if (lesson.equals(currentLesson)) {
                    //selected lesson
                    e.setTextColor(LearnUIManager.getInstance().getLessonActiveColor());
                } else {
                    e.resetTextColor();
                }
                e.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                lessonLabelMap.put(currentLesson, e);
                add(e);
            }
        }

        public void updateLessons(Lesson lesson) {
            for (Lesson curLesson : lessonLabelMap.keySet()) {
                MyLinkLabel lessonLabel = lessonLabelMap.get(curLesson);
                if (lesson.equals(curLesson)) {
                    lessonLabel.setTextColor(LearnUIManager.getInstance().getLessonActiveColor());
                } else {
                    lessonLabel.resetTextColor();
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            paintModuleCheckmarks(g);
        }

        private void paintModuleCheckmarks(Graphics g) {
            for (Lesson lesson : lessonLabelMap.keySet()) {
                if (lesson.getPassed()) {
                    JLabel jLabel = lessonLabelMap.get(lesson);
                    Point point = jLabel.getLocation();
                    if (!SystemInfo.isMac) {
                        LearnIcons.INSTANCE.getCheckMarkGray().paintIcon(this, g, point.x, point.y + 1);
                    } else {
                        LearnIcons.INSTANCE.getCheckMarkGray().paintIcon(this, g, point.x, point.y + 2);
                    }
                }
            }
        }


        class MyLinkLabel extends LinkLabel {

            Color userTextColor;

            MyLinkLabel(String text) {
                super(text, null);
            }

            @Override
            protected Color getTextColor() {
                return (userTextColor != null ? userTextColor : super.getTextColor());
            }

            void setTextColor(Color color) {
                userTextColor = color;
            }

            void resetTextColor() {
                userTextColor = null;
            }
        }
    }

    public void clickButton(){
        if (button != null && button.isEnabled() && button.isVisible()) button.doClick();
    }

    @Override
    public Dimension getPreferredSize() {
        if(lessonPanel.getMinimumSize() == null) return new Dimension(10, 10);
        if(modulePanel.getMinimumSize() == null) return new Dimension(10, 10);
        return new Dimension(
                (int) lessonPanel.getMinimumSize().getWidth() +
                        LearnUIManager.getInstance().getWestInset() +
                        LearnUIManager.getInstance().getEastInset(),
                (int) lessonPanel.getMinimumSize().getHeight() +
                        (int) modulePanel.getMinimumSize().getHeight() +
                        LearnUIManager.getInstance().getNorthInset() +
                        LearnUIManager.getInstance().getSouthInset());
    }

    @Override
    public Color getBackground(){
        if (!UIUtil.isUnderDarcula()) return LearnUIManager.getInstance().getBackgroundColor();
        else return UIUtil.getPanelBackground();
    }
}
