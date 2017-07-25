package training.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.text.SimpleAttributeSet;
import java.awt.*;

/**
 * Created by jetbrains on 12/08/16.
 */
public class LearnUIManager {

  //GENERAL UI SETTINGS
  private int width;

  //MAIN INSETS
  private int northInset;
  private int westInset;
  private int southInset;
  private int eastInset;
  private int checkWidth;
  private int checkRightIndent;
  private int leftIndent;

  //GAPS
  private int headerGap;
  private int moduleGap;
  private int progressGap;
  private int lessonGap;
  private int lessonNameGap;
  private int beforeButtonGap;
  private int afterButtonGap;
  private int afterCaptionGap;
  private int rateQuestionGap;
  private int groupGap;
  private int labelLineGap;
  private int labelVerticalGap;

  //FONTS
  private int fontSize;
  private String fontFace;
  private Font moduleNameFont;
  private Font plainFont;
  private Font italicFont;
  private Font boldFont;
  private Font lessonHeaderFont;
  private Font radioButtonLabelFont;

  //COLORS
  private Color defaultTextColor;
  private JBColor lessonActiveColor;
  private Color lessonCodeColor;
  private Color lessonLinkColor;
  private JBColor shortcutTextColor;
  private Color separatorColor;
  private JBColor shortcutBackgroundColor;
  private Color passedColor;
  private Color backgroundColor;
  private Color descriptionColor;
  private Color radioButtonLabelColor;

  private Color questionColor;

  //STYLES
  private static SimpleAttributeSet REGULAR = new SimpleAttributeSet();
  private static SimpleAttributeSet PARAGRAPH_STYLE = new SimpleAttributeSet();


  public static LearnUIManager getInstance() {
    return ServiceManager.getService(LearnUIManager.class);
  }

  public LearnUIManager() {
    width = 350;
    init();
    initGaps();
    initColors();
    initFonts();
  }

  private void init() {
    westInset = 13;
    northInset = 16;
    eastInset = 32;
    southInset = 32;

    checkWidth = LearnIcons.INSTANCE.getCheckMarkGray().getIconWidth();
    checkRightIndent = 5;
    leftIndent = 17;

    fontFace = UISettings.getShadowInstance().FONT_FACE;
    if (fontFace == null) fontFace = (new JLabel()).getFont().getFontName();
    fontSize = UISettings.getShadowInstance().FONT_SIZE;
    if (fontSize == 0) fontSize = 13;
  }

  private void initGaps() {
    lessonNameGap = 5;
    beforeButtonGap = 20;
    afterButtonGap = 44;
    lessonGap = 12;

    headerGap = 2;
    moduleGap = 20;
    progressGap = 12;

    afterCaptionGap = 12;
    rateQuestionGap = 16;
    groupGap = 24;

    labelLineGap = 12;
    labelVerticalGap = 5;
  }

  private void initColors() {
    shortcutTextColor = new JBColor(Gray._12, Gray._200);
    shortcutBackgroundColor = new JBColor(new Color(218, 226, 237), new Color(39, 43, 46));
    defaultTextColor = new JBColor(Gray._30, Gray._208);
    passedColor = new JBColor(Gray._105, Gray._103);
    backgroundColor = new Color(245, 245, 245);
    lessonCodeColor = new JBColor(new Color(27, 78, 128), new Color(85, 161, 255));
    lessonLinkColor = new JBColor(new Color(17, 96, 166), new Color(104, 159, 220));

    lessonActiveColor = new JBColor(new Color(0, 0, 0), Gray._202);
    descriptionColor = Gray._128;
    separatorColor = new JBColor(new Color(204, 204, 204), Gray._149);

    radioButtonLabelColor = descriptionColor;
    questionColor = lessonActiveColor;
  }

  private void initFonts() {
    plainFont = new Font(getFontFace(), Font.PLAIN, getFontSize());
    italicFont = plainFont.deriveFont(Font.ITALIC);
    boldFont = plainFont.deriveFont(Font.BOLD);
    lessonHeaderFont = new Font(getFontFace(), Font.BOLD, getFontSize() + 2);
    moduleNameFont = new Font(getFontFace(), Font.BOLD, getFontSize() + 1);
    radioButtonLabelFont = new Font(getFontFace(), Font.PLAIN, getFontSize() - 2);
  }

  public int getNorthInset() {
    return northInset;
  }

  public int getWestInset() {
    return westInset;
  }

  public Color getSeparatorColor() {
    return separatorColor;
  }

  public int getSouthInset() {
    return southInset;

  }

  public int getEastInset() {
    return eastInset;
  }

  public int getCheckWidth() {
    return checkWidth;
  }

  public int getCheckRightIndent() {
    return checkRightIndent;
  }

  public Border getEmptyBorder() {
    return new EmptyBorder(northInset, westInset, southInset, eastInset);
  }

  public Border getCheckmarkShiftBorder() {
    return new EmptyBorder(0, getCheckIndent(), 0, 0);
  }

  public int getFontSize() {
    return fontSize;
  }

  public String getFontFace() {
    return fontFace;
  }

  public int getCheckIndent() {
    return checkWidth + checkRightIndent;
  }

  public JBColor getShortcutBackgroundColor() {
    return shortcutBackgroundColor;
  }

  public Color getPassedColor() {
    return passedColor;
  }

  public Color getBackgroundColor() {
    return backgroundColor;
  }

  public Color getDefaultTextColor() {
    return defaultTextColor;
  }

  public Color getLessonCodeColor() {
    return lessonCodeColor;
  }

  public Color getLessonLinkColor() {
    return lessonLinkColor;
  }

  public JBColor getShortcutTextColor() {
    return shortcutTextColor;
  }

  public int getWidth() {
    return width;
  }

  public int getLessonGap() {
    return lessonGap;
  }

  public int getLessonNameGap() {
    return lessonNameGap;
  }

  public int getBeforeButtonGap() {
    return beforeButtonGap;
  }

  public Font getPlainFont() {
    return plainFont;
  }

  public Font getLessonHeaderFont() {
    return lessonHeaderFont;
  }

  public int getAfterButtonGap() {
    return afterButtonGap;
  }

  public JBColor getLessonActiveColor() {
    return lessonActiveColor;
  }

  public Font getBoldFont() {
    return boldFont;
  }

  public Font getItalicFont() {
    return italicFont;
  }

  public Color getDescriptionColor() {
    return descriptionColor;
  }

  public Font getModuleNameFont() {
    return moduleNameFont;
  }

  public int getHeaderGap() {
    return headerGap;
  }

  public int getModuleGap() {
    return moduleGap;
  }

  public int getProgressGap() {
    return progressGap;
  }

  public Color getQuestionColor() {
    return questionColor;
  }

  public int getAfterCaptionGap() {
    return afterCaptionGap;
  }

  public int getRateQuestionGap() {
    return rateQuestionGap;
  }

  public int getGroupGap() {
    return groupGap;
  }

  public int getLeftIndent() {
    return leftIndent;
  }

  public int getLabelLineGap() {
    return labelLineGap;
  }

  public int getLabelVerticalGap() {
    return labelVerticalGap;
  }

  public Font getRadioButtonLabelFont() {
    return radioButtonLabelFont;
  }

  public Color getRadioButtonLabelColor() {
    return radioButtonLabelColor;
  }
}

