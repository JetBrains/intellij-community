/*
 * Copyright 2002-2005 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.xpathView.ui;

import com.intellij.ui.ColorPanel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.intellij.plugins.xpathView.Config;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ConfigUI extends JPanel {

    private JCheckBox scrollToFirst;
    private JCheckBox useContextAtCursor;
    private JCheckBox highlightStartTagOnly;
    private JCheckBox addErrorStripe;
    private ColorPanel chooseHighlight;
    private ColorPanel chooseContext;

    public ConfigUI(Config configuration) {
        init();
        setConfig(configuration);
    }

    private void init() {
        setLayout(new BorderLayout());
        JPanel c = this;

        scrollToFirst = new JCheckBox(XPathBundle.message("settings.scroll.first.hit.into.visible.area"));

        useContextAtCursor = new JCheckBox(XPathBundle.message("settings.use.node.at.cursor.as.context.node"));
        useContextAtCursor.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stateChanged();
            }
        });

        highlightStartTagOnly = new JCheckBox(XPathBundle.message("settings.highlight.only.start.tag.instead.of.whole.tag.content"));
        addErrorStripe = new JCheckBox(XPathBundle.message("settings.add.error.stripe.markers.for.each.result"));

        JPanel settings = new JPanel(new BorderLayout());
        settings.setBorder(IdeBorderFactory.createTitledBorder(XPathBundle.message("settings.settings")));
        c.add(c = new JPanel(new BorderLayout()), BorderLayout.NORTH);
        c.add(settings, BorderLayout.NORTH);

        settings.add(scrollToFirst, BorderLayout.NORTH);
        settings.add(settings = new JPanel(new BorderLayout()), BorderLayout.SOUTH);
        settings.add(useContextAtCursor, BorderLayout.NORTH);
        settings.add(settings = new JPanel(new BorderLayout()), BorderLayout.SOUTH);
        settings.add(highlightStartTagOnly, BorderLayout.NORTH);
        settings.add(settings = new JPanel(new BorderLayout()), BorderLayout.SOUTH);
        settings.add(addErrorStripe, BorderLayout.NORTH);
        settings.add(new JPanel(new BorderLayout()), BorderLayout.SOUTH);

        JPanel colors = new JPanel(new GridBagLayout());
        colors.setBorder(IdeBorderFactory.createTitledBorder(XPathBundle.message("settings.colors")));
        c.add(c = new JPanel(new BorderLayout()), BorderLayout.SOUTH);
        c.add(colors, BorderLayout.NORTH);

      Insets emptyInsets = JBInsets.emptyInsets();
        Insets cpInsets = JBUI.insetsLeft(8);

        GridBagConstraints constraints = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, emptyInsets, 0, 0);

        colors.add(new JLabel(XPathBundle.message("settings.highlight.color")), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;

        chooseHighlight = new ColorPanel();
        constraints.insets = cpInsets;
        colors.add(chooseHighlight, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.weightx = 0;
        constraints.insets = emptyInsets;
        colors.add(new JLabel(XPathBundle.message("settings.context.node.color")), constraints);

        constraints.gridx = 1;
        constraints.gridy = 1;
        constraints.weightx = 1;
        constraints.insets = cpInsets;

        chooseContext = new ColorPanel();
        colors.add(chooseContext, constraints);
    }

    @NotNull
    public Config getConfig() {
        final Config config = new Config();
        config.setHighlightStartTagOnly(highlightStartTagOnly.isSelected());
        config.setUseContextAtCursor(useContextAtCursor.isSelected());
        config.setScrollToFirst(scrollToFirst.isSelected());
        config.setAddErrorStripe(addErrorStripe.isSelected());
        config.getAttributes().setBackgroundColor(chooseHighlight.getSelectedColor());
        if (useContextAtCursor.isSelected()) {
            config.getContextAttributes().setBackgroundColor(chooseContext.getSelectedColor());
        }
        return config;
    }

    public void setConfig(@NotNull Config configuration) {
        scrollToFirst.setSelected(configuration.isScrollToFirst());
        highlightStartTagOnly.setSelected(configuration.isHighlightStartTagOnly());
        useContextAtCursor.setSelected(configuration.isUseContextAtCursor());
        addErrorStripe.setSelected(configuration.isAddErrorStripe());
        chooseHighlight.setSelectedColor(configuration.getAttributes().getBackgroundColor());
        chooseContext.setSelectedColor(configuration.getContextAttributes().getBackgroundColor());
        stateChanged();
    }

    private void stateChanged() {
        chooseContext.setEnabled(useContextAtCursor.isSelected());
    }

    public static void main(String[] args) {
        @SuppressWarnings("HardCodedStringLiteral") JFrame test = new JFrame("Config test");
        test.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        final JPanel comp = new JPanel(new BorderLayout());
        comp.add(new ConfigUI(new Config()), BorderLayout.CENTER);
        test.getContentPane().add(comp);
        test.setSize(450, 450);
        test.setVisible(true);
    }
}
