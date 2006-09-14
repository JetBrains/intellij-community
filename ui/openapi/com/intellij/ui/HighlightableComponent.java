package com.intellij.ui;

import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntObjectHashMap;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * @author Eugene Belyaev
 */
public class HighlightableComponent extends JComponent {
  protected String myText = "";
  protected Icon myIcon;
  protected int myIconTextGap;
  protected ArrayList<HighlightedRegion> myHighlightedRegions;
  protected TIntObjectHashMap<FontMetrics> myFontMetrics;
  protected boolean myIsSelected;
  protected boolean myHasFocus;
  protected boolean myPaintUnfocusedSelection = false;

  public HighlightableComponent() {
    myIconTextGap = 4;
    myFontMetrics = new TIntObjectHashMap<FontMetrics>();
    setText("");
    fillFontMetricsMap();
    setOpaque(true);
  }

  protected void fillFontMetricsMap() {
    Font font = getFont();
    if (font != null){
      myFontMetrics.put(Font.PLAIN, getFontMetrics(font.deriveFont(Font.PLAIN)));
      myFontMetrics.put(Font.BOLD, getFontMetrics(font.deriveFont(Font.BOLD)));
      myFontMetrics.put(Font.ITALIC, getFontMetrics(font.deriveFont(Font.ITALIC)));
      myFontMetrics.put(Font.BOLD | Font.ITALIC, getFontMetrics(font.deriveFont(Font.BOLD | Font.ITALIC)));
    }
  }

  public void setText(String text) {
    if (text == null) {
      text = "";
    }
    myText = text;
    myHighlightedRegions = new ArrayList<HighlightedRegion>(4);
  }

  public void setIcon(Icon icon) {
    myIcon = icon;
  }

  public void setFont(Font font) {
    if (!font.equals(getFont())){
      super.setFont(font);
      fillFontMetricsMap();
    }
  }

  public void addHighlighter(int startOffset, int endOffset, TextAttributes attributes) {
    addHighlighter(0, startOffset, endOffset, attributes);
  }

  private void addHighlighter(int startIndex, int startOffset, int endOffset, TextAttributes attributes) {
    if (startOffset < 0) startOffset = 0;
    if (endOffset > myText.length()) endOffset = myText.length();

    if (startOffset >= endOffset) return;

    if (myHighlightedRegions.size() == 0){
      myHighlightedRegions.add(new HighlightedRegion(startOffset, endOffset, attributes));
    }
    else{
      for(int i = startIndex; i < myHighlightedRegions.size(); i++){
        HighlightedRegion hRegion = myHighlightedRegions.get(i);

        // must be before
        if (startOffset < hRegion.startOffset && endOffset <= hRegion.startOffset){
          myHighlightedRegions.add(i, new HighlightedRegion(startOffset, endOffset, attributes));
          break;
        }

        // must be after
        if (startOffset >= hRegion.endOffset){
          if (i == myHighlightedRegions.size() - 1){
            myHighlightedRegions.add(new HighlightedRegion(startOffset, endOffset, attributes));
            break;
          }
        }

        // must be before and overlap
        if (startOffset < hRegion.startOffset && endOffset > hRegion.startOffset){

          if (endOffset < hRegion.endOffset){
            myHighlightedRegions.add(i, new HighlightedRegion(startOffset, hRegion.startOffset, attributes));
            myHighlightedRegions.add(i + 1, new HighlightedRegion(hRegion.startOffset, endOffset, TextAttributes.merge(hRegion.textAttributes, attributes)));
            hRegion.startOffset = endOffset;
            break;
          }

          if (endOffset == hRegion.endOffset){
            myHighlightedRegions.remove(hRegion);
            myHighlightedRegions.add(i, new HighlightedRegion(startOffset, hRegion.startOffset, attributes));
            myHighlightedRegions.add(i + 1, new HighlightedRegion(hRegion.startOffset, endOffset, TextAttributes.merge(hRegion.textAttributes, attributes)));
            break;
          }

          if (endOffset > hRegion.endOffset){
            myHighlightedRegions.remove(hRegion);
            myHighlightedRegions.add(i, new HighlightedRegion(startOffset, hRegion.startOffset, attributes));
            myHighlightedRegions.add(i + 1, new HighlightedRegion(hRegion.startOffset, hRegion.endOffset, TextAttributes.merge(hRegion.textAttributes, attributes)));

            if (i < myHighlightedRegions.size() - 1){
              addHighlighter(i + 1, hRegion.endOffset, endOffset, attributes);
            }
            else{
              myHighlightedRegions.add(i + 2, new HighlightedRegion(hRegion.endOffset, endOffset, attributes));
            }
            break;
          }
        }

        // must be after and overlap or full overlap
        if (startOffset >= hRegion.startOffset && startOffset < hRegion.endOffset){

          int oldEndOffset = hRegion.endOffset;

          hRegion.endOffset = startOffset;

          if (endOffset < oldEndOffset){
            myHighlightedRegions.add(i + 1, new HighlightedRegion(startOffset, endOffset, TextAttributes.merge(hRegion.textAttributes, attributes)));
            myHighlightedRegions.add(i + 2, new HighlightedRegion(endOffset, oldEndOffset, hRegion.textAttributes));

            if (startOffset == hRegion.startOffset){
              myHighlightedRegions.remove(hRegion);
            }

            break;
          }

          if (endOffset == oldEndOffset){
            myHighlightedRegions.add(i + 1, new HighlightedRegion(startOffset, oldEndOffset, TextAttributes.merge(hRegion.textAttributes, attributes)));

            if (startOffset == hRegion.startOffset){
              myHighlightedRegions.remove(hRegion);
            }

            break;
          }

          if (endOffset > oldEndOffset){
            myHighlightedRegions.add(i + 1, new HighlightedRegion(startOffset, oldEndOffset, TextAttributes.merge(hRegion.textAttributes, attributes)));
            if (i < myHighlightedRegions.size() - 1){
              addHighlighter(i + 1, oldEndOffset, endOffset, attributes);
            }
            else{
              myHighlightedRegions.add(i + 2, new HighlightedRegion(hRegion.endOffset, endOffset, attributes));
            }

            if (startOffset == hRegion.startOffset){
              myHighlightedRegions.remove(hRegion);
            }

            break;
          }
        }
      }
    }
  }

  public void setIconTextGap(int gap) {
    myIconTextGap = Math.max(gap, 2);
  }

  public int getIconTextGap() {
    return myIconTextGap;
  }

  private Color myEnforcedBackground = null;
  protected void enforceBackgroundOutsideText(Color bg) {
    myEnforcedBackground = bg;
  }

  protected void paintComponent(Graphics g) {

    // determine color of background

    Color bgColor;
    Color fgColor;
    boolean paintHighlightsBackground;
    boolean paintHighlightsForeground;
    if (myIsSelected && (myHasFocus || myPaintUnfocusedSelection)) {
      bgColor = UIUtil.getTreeSelectionBackground();
      fgColor = UIUtil.getTreeSelectionForeground();
      paintHighlightsBackground = false;
      paintHighlightsForeground = false;
    }
    else {
      bgColor = myEnforcedBackground == null ? UIUtil.getTreeTextBackground() : myEnforcedBackground;
      fgColor = getForeground();
      paintHighlightsBackground = isOpaque();
      paintHighlightsForeground = true;
    }

    // paint background

    int textOffset = getTextOffset();
    int offset = textOffset;

    if (isOpaque()) {
      g.setColor(getBackground());
      g.fillRect(0,0,textOffset-2,getHeight()-1);
      g.setColor(bgColor);
      g.fillRect(textOffset-2, 0, getWidth()-1, getHeight()-1);
    }

    // paint icon

    if (myIcon != null) {
      myIcon.paintIcon(this, g, 0, (getHeight() - myIcon.getIconHeight()) / 2);
    }

    // paint text

    FontMetrics defFontMetrics = getFontMetrics(getFont());

    if (myText == null) {
      myText = "";
    }
    // center text inside the component:
    final int yOffset = (getHeight() - defFontMetrics.getMaxAscent() - defFontMetrics.getMaxDescent()) / 2 + defFontMetrics.getMaxAscent() - 1;
    if (myHighlightedRegions.size() == 0){
      g.setColor(fgColor);
      g.drawString(myText, textOffset, yOffset/*defFontMetrics.getMaxAscent()*/);
    }
    else{
      int endIndex = 0;
      for (HighlightedRegion hRegion : myHighlightedRegions) {

        String text = myText.substring(endIndex, hRegion.startOffset);
        endIndex = hRegion.endOffset;

        // draw plain text

        if (text.length() != 0) {
          g.setColor(fgColor);
          g.setFont(defFontMetrics.getFont());

          g.drawString(text, offset, yOffset/*defFontMetrics.getMaxAscent()*/);

          offset += defFontMetrics.stringWidth(text);
        }

        FontMetrics fontMetrics = myFontMetrics.get(hRegion.textAttributes.getFontType());

        text = myText.substring(hRegion.startOffset, hRegion.endOffset);

        // paint highlight background

        if (hRegion.textAttributes.getBackgroundColor() != null && paintHighlightsBackground) {
          g.setColor(hRegion.textAttributes.getBackgroundColor());
          g.fillRect(offset, 0, fontMetrics.stringWidth(text), fontMetrics.getHeight() + fontMetrics.getLeading());
        }

        // draw highlight text

        if (hRegion.textAttributes.getForegroundColor() != null && paintHighlightsForeground) {
          g.setColor(hRegion.textAttributes.getForegroundColor());
        }
        else {
          g.setColor(fgColor);
        }

        g.setFont(fontMetrics.getFont());
        g.drawString(text, offset, yOffset/*fontMetrics.getMaxAscent()*/);

        // draw highlight underscored line

        if (hRegion.textAttributes.getEffectColor() != null) {
          g.setColor(hRegion.textAttributes.getEffectColor());
          int y = yOffset/*fontMetrics.getMaxAscent()*/ + 2;
          UIUtil.drawLine(g, offset, y, offset + fontMetrics.stringWidth(text) - 1, y);
        }

        // draw highlight border

        if (hRegion.textAttributes.getEffectColor() != null && hRegion.textAttributes.getEffectType() == EffectType.BOXED) {
          g.setColor(hRegion.textAttributes.getEffectColor());
          g.drawRect(offset, 0, fontMetrics.stringWidth(text) - 1, fontMetrics.getHeight() + fontMetrics.getLeading() - 1);
        }

        offset += fontMetrics.stringWidth(text);
      }

      String text = myText.substring(endIndex, myText.length());

      if (text.length() != 0){
        g.setColor(fgColor);
        g.setFont(defFontMetrics.getFont());

        g.drawString(text, offset, yOffset/*defFontMetrics.getMaxAscent()*/);
      }
    }

    // paint border

    if (myIsSelected){
      g.setColor(UIUtil.getTreeSelectionBorderColor());
      UIUtil.drawDottedRectangle(g, textOffset - 2, 0, getWidth() - 1, getHeight() - 1);
    }

    super.paintComponent(g);
  }

  private int getTextOffset() {
    if (myIcon == null){
      return 2;
    }
    return myIcon.getIconWidth() + myIconTextGap;
  }

  public Dimension getPreferredSize() {
    FontMetrics defFontMetrics = getFontMetrics(getFont());

    int width = getTextOffset();

    if (myText.length() != 0){
      if (myHighlightedRegions.size() == 0){
        width += defFontMetrics.stringWidth(myText);
      }
      else{
        int endIndex = 0;
        for (HighlightedRegion hRegion : myHighlightedRegions) {

          String text = myText.substring(endIndex, hRegion.startOffset);
          endIndex = hRegion.endOffset;

          width += defFontMetrics.stringWidth(text);

          if (hRegion.endOffset > myText.length()) {
            if (hRegion.startOffset < myText.length()) {
              text = myText.substring(hRegion.startOffset);
            }
            else {
              text = "";
            }
          }
          else {
            text = myText.substring(hRegion.startOffset, hRegion.endOffset);
          }

          FontMetrics fontMetrics = myFontMetrics.get(hRegion.textAttributes.getFontType());
          width += fontMetrics.stringWidth(text);
        }
        width += defFontMetrics.stringWidth(myText.substring(endIndex, myText.length()));
      }
    }

    int height = defFontMetrics.getHeight() + defFontMetrics.getLeading();

    if (myIcon != null){
      height = Math.max(myIcon.getIconHeight() + defFontMetrics.getLeading(), height);
    }

    return new Dimension(width + 2, height);
  }
}
