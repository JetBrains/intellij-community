package com.intellij.openapi.wm.impl;

import com.intellij.openapi.wm.ToolWindowAnchor;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.metal.MetalToggleButtonUI;
import java.awt.*;
import java.awt.geom.AffineTransform;

/**
 * @author Vladimir Kondratyev
 */
final class StripeButtonUI extends MetalToggleButtonUI{
  private static final StripeButtonUI ourInstance=new StripeButtonUI();

  private static final Rectangle ourIconRect=new Rectangle();
  private static final Rectangle ourTextRect=new Rectangle();
  private static final Rectangle ourViewRect=new Rectangle();
  private static Insets ourViewInsets=new Insets(0,0,0,0);

  private StripeButtonUI(){}

  /** Invoked by reflection */
  public static ComponentUI createUI(final JComponent c){
    return ourInstance;
  }

  public Dimension getPreferredSize(final JComponent c){
    final StripeButton button=(StripeButton)c;
    final Dimension dim=super.getPreferredSize(button);

    dim.width=(int)(4+dim.width*1.1f);
    dim.height+=4;

    final ToolWindowAnchor anchor=button.getWindowInfo().getAnchor();
    if(ToolWindowAnchor.LEFT==anchor||ToolWindowAnchor.RIGHT==anchor){
      return new Dimension(dim.height,dim.width);
    } else{
      return dim;
    }
  }

  public void paint(final Graphics g,final JComponent c){
    final StripeButton button=(StripeButton)c;

    final String text=button.getText();
    final Icon icon=(button.isEnabled()) ? button.getIcon() : button.getDisabledIcon();

    if((icon==null)&&(text==null)){
      return;
    }

    final FontMetrics fm=button.getFontMetrics(button.getFont());
    ourViewInsets=c.getInsets(ourViewInsets);

    ourViewRect.x=ourViewInsets.left;
    ourViewRect.y=ourViewInsets.top;

    final ToolWindowAnchor anchor=button.getWindowInfo().getAnchor();

    // Use inverted height & width
    if(ToolWindowAnchor.RIGHT==anchor||ToolWindowAnchor.LEFT==anchor){
      ourViewRect.height=c.getWidth()-(ourViewInsets.left+ourViewInsets.right);
      ourViewRect.width=c.getHeight()-(ourViewInsets.top+ourViewInsets.bottom);
    } else{
      ourViewRect.height=c.getHeight()-(ourViewInsets.left+ourViewInsets.right);
      ourViewRect.width=c.getWidth()-(ourViewInsets.top+ourViewInsets.bottom);
    }

    ourIconRect.x=ourIconRect.y=ourIconRect.width=ourIconRect.height=0;
    ourTextRect.x=ourTextRect.y=ourTextRect.width=ourTextRect.height=0;

    final String clippedText=SwingUtilities.layoutCompoundLabel(
      c,fm,text,icon,
      button.getVerticalAlignment(),button.getHorizontalAlignment(),
      button.getVerticalTextPosition(),button.getHorizontalTextPosition(),
      ourViewRect,ourIconRect,ourTextRect,
      button.getText()==null ? 0 : defaultTextIconGap
    );

    // Paint button's background

    final Graphics2D g2=(Graphics2D)g;
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);

    final ButtonModel model=button.getModel();
    if (model.isArmed() && model.isPressed() || model.isSelected()) {
//      g.setColor(button.getBackground().darker());
//      g.fillRect(0, 0, button.getWidth(), button.getHeight());
      g.setColor(button.getBackground());
      g.fillRect(0, 0, button.getWidth(), button.getHeight());
      g.setColor(button.getBackground().darker());
      g.fillRoundRect(3, 3, button.getWidth() - 6, button.getHeight() - 6, 5, 5);
    } else /*if (button.isOpaque())*/ {
      g.setColor(button.getBackground());
      g.fillRect(0, 0, button.getWidth(), button.getHeight());
      g.setColor(Color.GRAY);
      g.drawRoundRect(3, 3, button.getWidth() - 6, button.getHeight() - 6, 5, 5);
    }

    AffineTransform tr=null;
    if(ToolWindowAnchor.RIGHT==anchor||ToolWindowAnchor.LEFT==anchor){
      tr=g2.getTransform();
      if(ToolWindowAnchor.RIGHT==anchor){
        if(icon != null){ // do not rotate icon
          icon.paintIcon(c, g2, ourIconRect.y, ourIconRect.x);
        }
        g2.rotate(Math.PI/2);
        g2.translate(0,-c.getWidth());
      } else {
        if(icon != null){ // do not rotate icon
          icon.paintIcon(c, g2, ourIconRect.y, c.getHeight() - ourIconRect.x - icon.getIconHeight());
        }
        g2.rotate(-Math.PI/2);
        g2.translate(-c.getHeight(),0);
      }
    }
    else{
      if(icon!=null){
        icon.paintIcon(c,g2,ourIconRect.x,ourIconRect.y);
      }
    }

    // paint text

    if(text!=null){
      if(model.isEnabled()){
        if(model.isArmed()&&model.isPressed()||model.isSelected()){
          g.setColor(button.getBackground());
        } else{
          g.setColor(button.getForeground());
        }
      } else{
        g.setColor(button.getBackground().darker());
      }
      /* Draw the Text */
      if(model.isEnabled()){
        /*** paint the text normally */
        g.setColor(button.getForeground());
        BasicGraphicsUtils.drawString(g,clippedText,button.getMnemonic2(),ourTextRect.x,ourTextRect.y+fm.getAscent());
      } else{
        /*** paint the text disabled ***/
        if(model.isSelected()){
          g.setColor(c.getBackground());
        } else{
          g.setColor(getDisabledTextColor());
        }
        BasicGraphicsUtils.drawString(g,clippedText,button.getMnemonic2(),ourTextRect.x,ourTextRect.y+fm.getAscent());
      }
    }
    if(ToolWindowAnchor.RIGHT==anchor||ToolWindowAnchor.LEFT==anchor){
      g2.setTransform(tr);
    }
  }
}