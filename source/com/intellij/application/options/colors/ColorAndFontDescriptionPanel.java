/**
 * @author cdr
 */
package com.intellij.application.options.colors;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.ui.ColorPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ColorAndFontDescriptionPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.colors.ColorAndFontDescriptionPanel");

  private ColorPanel myBackgroundChooser = new ColorPanel();
  private ColorPanel myForegroundChooser = new ColorPanel();
  private ColorPanel myEffectsColorChooser = new ColorPanel();
  private ColorPanel myErrorStripeColorChooser = new ColorPanel();

  private JCheckBox myCbBackground = new JCheckBox(ApplicationBundle.message("checkbox.color.background"));
  private JCheckBox myCbForeground = new JCheckBox(ApplicationBundle.message("checkbox.color.foreground"));
  private JCheckBox myCbEffects = new JCheckBox(ApplicationBundle.message("checkbox.color.effects"));
  private JCheckBox myCbErrorStripe = new JCheckBox(ApplicationBundle.message("checkbox.color.error.stripe.mark"));

  private static final String BORDERED_EFFECT = ApplicationBundle.message("combobox.effect.bordered");
  private static final String UNDERSCORED_EFFECT = ApplicationBundle.message("combobox.effect.underscored");
  private static final String UNDERWAVED_EFFECT = ApplicationBundle.message("combobox.effect.underwaved");
  private static final String STRIKEOUT_EFFECT = ApplicationBundle.message("combobox.effect.strikeout");

  private JComboBox myEffectsCombo = new JComboBox(
    new String[]{UNDERSCORED_EFFECT, UNDERWAVED_EFFECT, BORDERED_EFFECT, STRIKEOUT_EFFECT});

  private JCheckBox myCbBold = new JCheckBox(ApplicationBundle.message("checkbox.font.bold"));
  private JCheckBox myCbItalic = new JCheckBox(ApplicationBundle.message("checkbox.font.italic"));
  private boolean updatingEffects;
  private ActionListener myActionListener;


  public ColorAndFontDescriptionPanel() {
    super(new BorderLayout());

    JPanel settingsPanel = createSettingsPanel();

    add(settingsPanel, BorderLayout.CENTER);
    setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 4));
  }

  private JPanel createSettingsPanel() {
    JPanel settingsPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 0;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.insets = new Insets(4, 4, 4, 4);

    settingsPanel.add(createFontSettingsPanel(), gbConstraints);
    settingsPanel.add(createColorSettingsPanel(), gbConstraints);
    gbConstraints.weighty = 1;
    settingsPanel.add(new TailPanel(), gbConstraints);
    return settingsPanel;
  }

  private JPanel createFontSettingsPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridy = 1;
    gbConstraints.gridx = 1;
    panel.add(new JLabel(ApplicationBundle.message("label.font.type")), gbConstraints);
    gbConstraints.gridx = 2;
    panel.add(myCbBold, gbConstraints);
    gbConstraints.gridx = 3;
    panel.add(myCbItalic, gbConstraints);
    gbConstraints.gridx = 4;
    gbConstraints.weightx = 1;
    panel.add(new TailPanel(), gbConstraints);

    myCbBold.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myActionListener != null) {
          myActionListener.actionPerformed(e);
        }
      }
    });
    myCbItalic.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myActionListener != null) {
          myActionListener.actionPerformed(e);
        }
      }
    });
    return panel;
  }

  private JPanel createColorSettingsPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());

    GridBagConstraints gc = new GridBagConstraints();
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 0;
    gc.weighty = 1;
    gc.gridwidth = 1;

    gc.gridx = 0;
    gc.gridy = 0;
    panel.add(myCbForeground, gc);

    gc.gridy++;
    panel.add(myCbBackground, gc);

    gc.gridy++;
    panel.add(myCbErrorStripe, gc);

    gc.gridy++;
    panel.add(myCbEffects, gc);

    gc.gridy++;
    gc.insets = new Insets(0, 10, 0, 0);
    gc.weighty = 0;
    panel.add(myEffectsCombo, gc);

    gc.gridx = 1;
    gc.gridy = 0;
    panel.add(myForegroundChooser, gc);

    gc.gridy++;
    panel.add(myBackgroundChooser, gc);

    gc.gridy++;
    panel.add(myErrorStripeColorChooser, gc);

    gc.gridy += 2;
    panel.add(myEffectsColorChooser, gc);

    gc.gridx = 2;
    gc.gridy = 0;
    gc.weightx = 1;
    panel.add(new TailPanel(), gc);

    gc.gridy++;
    panel.add(new TailPanel(), gc);

    gc.gridy++;
    panel.add(new TailPanel(), gc);

    gc.gridy++;
    panel.add(new TailPanel(), gc);


    myEffectsColorChooser.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (myActionListener != null) {
            myActionListener.actionPerformed(e);
          }
        }
      }
    );

    myForegroundChooser.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (myActionListener != null) {
            myActionListener.actionPerformed(e);
          }
        }
      }
    );

    myCbForeground.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          myForegroundChooser.setEnabled(myCbForeground.isSelected());
          if (myActionListener != null) {
            myActionListener.actionPerformed(e);
          }
        }
      }
    );

    myBackgroundChooser.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (myActionListener != null) {
            myActionListener.actionPerformed(e);
          }
        }
      }
    );
    myErrorStripeColorChooser.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (myActionListener != null) {
            myActionListener.actionPerformed(e);
          }
        }
      }
    );

    myCbBackground.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          myBackgroundChooser.setEnabled(myCbBackground.isSelected());
          if (myActionListener != null) {
            myActionListener.actionPerformed(e);
          }
        }
      }
    );
    myCbErrorStripe.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          myErrorStripeColorChooser.setEnabled(myCbErrorStripe.isSelected());
          if (myActionListener != null) {
            myActionListener.actionPerformed(e);
          }
        }
      }
    );


    myCbEffects.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          boolean checked = myCbEffects.isSelected();
          myEffectsColorChooser.setEnabled(checked);
          myEffectsCombo.setEnabled(checked);
          if (myActionListener != null) {
            myActionListener.actionPerformed(e);
          }
        }
      }
    );

    myEffectsCombo.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (!updatingEffects) {
          if (myActionListener != null) {
            myActionListener.actionPerformed(e);
          }
        }
      }
    });

    return panel;
  }

  public void addActionListener(ActionListener actionListener) {
    myActionListener = actionListener;
  }

  public void resetDefault() {
    myCbBold.setSelected(false);
    myCbBold.setEnabled(false);
    myCbItalic.setSelected(false);
    myCbItalic.setEnabled(false);
    updateColorChooser(myCbForeground, myForegroundChooser, false, false, null);
    updateColorChooser(myCbBackground, myBackgroundChooser, false, false, null);
    updateColorChooser(myCbErrorStripe, myErrorStripeColorChooser, false, false, null);
    updateColorChooser(myCbEffects, myEffectsColorChooser, false, false, null);
    myEffectsCombo.setEnabled(false);
  }

  private static void updateColorChooser(JCheckBox checkBox,
                                         ColorPanel colorPanel,
                                         boolean isEnabled,
                                         boolean isChecked,
                                         Color color) {
    checkBox.setEnabled(isEnabled);
    checkBox.setSelected(isChecked);
    if (color != null) {
      colorPanel.setSelectedColor(color);
    }
    else {
      colorPanel.setSelectedColor(Color.white);
    }
    colorPanel.setEnabled(isChecked);
  }

  public void reset(ColorAndFontDescription description) {
    if (description.isFontEnabled()) {
      myCbBold.setEnabled(true);
      myCbItalic.setEnabled(true);
      int fontType = description.getFontType();
      myCbBold.setSelected((fontType & Font.BOLD) != 0);
      myCbItalic.setSelected((fontType & Font.ITALIC) != 0);
    } else {
      myCbBold.setSelected(false);
      myCbBold.setEnabled(false);
      myCbItalic.setSelected(false);
      myCbItalic.setEnabled(false);
    }

    updateColorChooser(myCbForeground, myForegroundChooser, description.isForegroundEnabled(),
                       description.isForegroundChecked(), description.getForegroundColor());

    updateColorChooser(myCbBackground, myBackgroundChooser, description.isBackgroundEnabled(),
                       description.isBackgroundChecked(), description.getBackgroundColor());

    updateColorChooser(myCbErrorStripe, myErrorStripeColorChooser, description.isErrorStripeEnabled(),
                       description.isErrorStripeChecked(), description.getErrorStripeColor());

    EffectType effectType = description.getEffectType();
    updateColorChooser(myCbEffects, myEffectsColorChooser, description.isEffectsColorEnabled(),
                       description.isEffectsColorChecked(), description.getEffectColor());

    if (description.isEffectsColorEnabled() && description.isEffectsColorChecked()) {
      myEffectsCombo.setEnabled(true);
      updatingEffects = true;
      if (effectType == EffectType.BOXED) {
        myEffectsCombo.setSelectedItem(BORDERED_EFFECT);
      }
      else if (effectType == EffectType.LINE_UNDERSCORE) {
        myEffectsCombo.setSelectedItem(UNDERSCORED_EFFECT);
      }
      else if (effectType == EffectType.WAVE_UNDERSCORE) {
        myEffectsCombo.setSelectedItem(UNDERWAVED_EFFECT);
      }
      else if (effectType == EffectType.STRIKEOUT) {
        myEffectsCombo.setSelectedItem(STRIKEOUT_EFFECT);
      }
      else {
        LOG.assertTrue(false);
      }
      updatingEffects = false;
    }
    else {
      myEffectsCombo.setEnabled(false);
    }
  }

  public void apply(ColorAndFontDescription description, EditorColorsScheme scheme) {
    if (description != null) {
      int fontType = Font.PLAIN;
      if (myCbBold.isSelected()) fontType += Font.BOLD;
      if (myCbItalic.isSelected()) fontType += Font.ITALIC;
      description.setFontType(fontType);
      description.setForegroundChecked(myCbForeground.isSelected());
      description.setForegroundColor(myForegroundChooser.getSelectedColor());
      description.setBackgroundChecked(myCbBackground.isSelected());
      description.setBackgroundColor(myBackgroundChooser.getSelectedColor());
      description.setErrorStripeChecked(myCbErrorStripe.isSelected());
      description.setErrorStripeColor(myErrorStripeColorChooser.getSelectedColor());
      description.setEffectsColorChecked(myCbEffects.isSelected());
      description.setEffectColor(myEffectsColorChooser.getSelectedColor());

      if (myEffectsCombo.isEnabled()) {
        Object effectType = myEffectsCombo.getModel().getSelectedItem();

        if (BORDERED_EFFECT.equals(effectType)) {
          description.setEffectType(EffectType.BOXED);
        }
        else if (UNDERWAVED_EFFECT.equals(effectType)) {
          description.setEffectType(EffectType.WAVE_UNDERSCORE);
        }
        else if (UNDERSCORED_EFFECT.equals(effectType)) {
          description.setEffectType(EffectType.LINE_UNDERSCORE);
        }
        else if (STRIKEOUT_EFFECT.equals(effectType)) {
          description.setEffectType(EffectType.STRIKEOUT);
        }
        else {
          LOG.assertTrue(false);
        }
      }
      description.apply(scheme);
    }
  }
}