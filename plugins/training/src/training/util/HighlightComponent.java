package training.util;

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Created by karashevich on 26/10/15.
 */
public class HighlightComponent extends JComponent {
    @NotNull
    private Color myColor;
    @Nullable
    private String myName;
    @Nullable
    private JLabel myLabel;
    @Nullable private JButton myCloseButton;
    @Nullable private JLabel myDescriptionLabel;

    final private int verticalSpace = 12;

    public HighlightComponent(@NotNull final Color c, @Nullable String componentName, @Nullable String description, @Nullable Integer width, boolean showCloseButton, boolean showNameOfComponent) {
        myColor = c;
        myName = componentName;
        BoxLayout layoutManager = new BoxLayout(this, BoxLayout.PAGE_AXIS);
        setLayout(layoutManager);
        if (componentName != null && showNameOfComponent) {

            myLabel = new JLabel();
            myLabel.setText(myName);
            myLabel.setFont(new Font(UIUtil.getLabelFont().getName(), Font.BOLD, 38));
            myLabel.setForeground(Color.WHITE);
            myLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            this.add(Box.createVerticalGlue());
            this.add(myLabel);
            this.add(Box.createRigidArea(new Dimension(0, verticalSpace)));
        } else {
            this.add(Box.createVerticalGlue());
        }
        if (description != null && showNameOfComponent) {

            myDescriptionLabel = new JLabel(XmlUtil.addHtmlTags(description));
            myDescriptionLabel.setForeground(Color.WHITE);
            if (width != null) {
                myDescriptionLabel.setBorder(new EmptyBorder(0, width / 3, 0, width / 3));
            }
            myDescriptionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            this.add(myDescriptionLabel);
            this.add(Box.createRigidArea(new Dimension(0, verticalSpace)));

        }
        if (showCloseButton) {
            myCloseButton = new JButton("Got it!");
            myCloseButton.setAlignmentX(Component.CENTER_ALIGNMENT);
            this.add(myCloseButton);
        }
        this.add(Box.createVerticalGlue());
    }

    public void setCloseButtonAction(final Runnable runnable){
        if (myCloseButton != null) {
            myCloseButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    runnable.run();
                }
            });
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D)g;
        Color oldColor = g2d.getColor();
        g2d.setColor(myColor);
        Composite old = g2d.getComposite();
//        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        Rectangle r = getBounds();
        g2d.fillRect(0, 0, r.width, r.height);
        g2d.setColor(myColor);
        g2d.drawRect(0, 0, r.width - 1, r.height - 1);
        g2d.setComposite(old);
        g2d.setColor(oldColor);

    }
}
