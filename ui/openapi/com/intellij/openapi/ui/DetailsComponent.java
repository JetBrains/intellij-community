package com.intellij.openapi.ui;

import com.intellij.openapi.wm.impl.content.GraphicsConfig;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.geom.GeneralPath;

public class DetailsComponent  {

  private JPanel myComponent;

  private JComponent myContentWrapper;


  private Banner myBannerLabel;

  private JLabel myEmptyContentLabel;
  private NonOpaquePanel myBanner;
  private ChooseView myChooseView;

  public DetailsComponent() {
    myComponent = new JPanel(new BorderLayout()) {
      protected void paintComponent(final Graphics g) {
        if (NullableComponent.Check.isNull(myContentWrapper)) return;

        GraphicsConfig c = new GraphicsConfig(g);
        c.setAntialiasing(true);

        int arc = 8;

        Insets insets = getInsets();
        if (insets == null) {
          insets = new Insets(0, 0, 0, 0);
        }

        g.setColor(UIUtil.getFocusedFillColor());

        final Rectangle banner = myBanner.getBounds();
        final GeneralPath header = new GeneralPath();

        final int leftX = insets.left;
        final int leftY = insets.top;
        final int rightX = insets.left + getWidth() - 1 - insets.right;
        final int rightY = banner.y + banner.height;

        header.moveTo(leftX, rightY);
        header.lineTo(leftX, leftY + arc);
        header.quadTo(leftX, leftY, leftX + arc, leftY);
        header.lineTo(rightX - arc, leftY);
        header.quadTo(rightX, leftY, rightX, leftY + arc);
        header.lineTo(rightX, rightY);
        header.closePath();

        c.getG().fill(header);

        g.setColor(UIUtil.getFocusedBoundsColor());

        c.getG().draw(header);

        final int down = getHeight() - insets.top - insets.bottom - 1;
        g.drawLine(leftX, rightY, leftX, down);
        g.drawLine(rightX, rightY, rightX, down);
        g.drawLine(leftX, down, rightX, down);

        c.restore();
      }
    };

    myComponent.setOpaque(false);

    myBanner = new NonOpaquePanel(new BorderLayout());
    myBannerLabel = new Banner();

    myChooseView = new ChooseView();
    myChooseView.setBorder(new EmptyBorder(0, 2, 0, 8));

    myBanner.add(myBannerLabel, BorderLayout.CENTER);
    myBanner.add(myChooseView, BorderLayout.EAST);

    myComponent.add(myBanner, BorderLayout.NORTH);
    myEmptyContentLabel = new JLabel("", JLabel.CENTER);
  }

  public void setContent(@Nullable JComponent c) {
    if (myContentWrapper != null) {
      myComponent.remove(myContentWrapper);
    } 

    myContentWrapper = new MyWrapper(c);

    myContentWrapper.setOpaque(false);
    myContentWrapper.setBorder(new EmptyBorder(5, 5, 5, 5));
    myComponent.add(myContentWrapper, BorderLayout.CENTER);

    myComponent.revalidate();
    myComponent.repaint();
  }




  public void setText(String text) {
    myBannerLabel.setText(text);
  }

  public void setEmptyContentText(final String emptyContentText) {
    final String s = "<html><body><center>" + emptyContentText + "</center></body><html>";
    myEmptyContentLabel.setText(s);
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public void setBannerMinHeight(final int height) {
    myBannerLabel.setMinHeight(height);
  }

  public ChooseView getChooseView() {
    return myChooseView;
  }

  public void disposeUIResources() {
    setContent(null);
  }

  private class Banner extends JLabel {

    private int myBannerMinHeight;

    public Banner() {
      super("", JLabel.LEFT);
      setFont(getFont().deriveFont(Font.BOLD));
      setBorder(new EmptyBorder(2, 6, 2, 4));
    }

    public Dimension getMinimumSize() {
      final Dimension size = super.getMinimumSize();
      size.height = myBannerMinHeight > 0 ? myBannerMinHeight : size.height;
      return size;
    }

    public Dimension getPreferredSize() {
      final Dimension size = super.getPreferredSize();
      size.height = getMinimumSize().height;
      return size;
    }

    public void setMinHeight(final int height) {
      myBannerMinHeight = height;
      revalidate();
      repaint();
    }
  }

  public static void main(String[] args) {
    final JFrame frame = new JFrame();
    frame.getContentPane().setLayout(new BorderLayout());
    final JPanel content = new JPanel(new BorderLayout());

    final DetailsComponent d = new DetailsComponent();
    content.add(d.getComponent(), BorderLayout.CENTER);

    d.setText("This is a Tree");
    final JTree c = new JTree();
    c.setBorder(new LineBorder(Color.red));
    d.setContent(c);

    frame.getContentPane().add(content, BorderLayout.CENTER);

    frame.setBounds(300, 300, 300, 300);
    frame.show();
  }


  public static interface Facade {

    DetailsComponent getDetailsComponent();

  }

  private class MyWrapper extends Wrapper implements NullableComponent {
    public MyWrapper(final JComponent c) {
      super(c == null ? DetailsComponent.this.myEmptyContentLabel : c);
    }

    public boolean isNull() {
      return getTargetComponent() == myEmptyContentLabel;
    }
  }

}
