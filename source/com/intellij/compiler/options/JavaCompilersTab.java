package com.intellij.compiler.options;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.JavacSettings;
import com.intellij.compiler.JikesSettings;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 30, 2004
 */
public class JavaCompilersTab implements Configurable{
  private JPanel myPanel;
  private JPanel myContentPanel;
  private CardLayout myCardLayout;
  private JRadioButton myRbSetJavacCompiler;
  private JRadioButton myRbSetJikesCompiler;

  private String myDefaultCompiler;
  private JikesConfigurable myJikesConfigurable;
  private JavacConfigurable myJavacConfigurable;
  private CompilerConfiguration myCompilerConfiguration;

  public JavaCompilersTab(final Project project) {
    myCompilerConfiguration = CompilerConfiguration.getInstance(project);
    myJavacConfigurable = new JavacConfigurable(JavacSettings.getInstance(project));
    myJikesConfigurable = new JikesConfigurable(JikesSettings.getInstance(project));

    myCardLayout = new CardLayout();
    myContentPanel.setLayout(myCardLayout);
    myContentPanel.add(myJavacConfigurable.createComponent(), CompilerConfiguration.JAVAC);
    myContentPanel.add(myJikesConfigurable.createComponent(), CompilerConfiguration.JIKES);

    final ButtonGroup group = new ButtonGroup();
    group.add(myRbSetJavacCompiler);
    group.add(myRbSetJikesCompiler);
    myRbSetJavacCompiler.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent e) {
          setDefaultCompiler(CompilerConfiguration.JAVAC);
        }
      }
    );

    myRbSetJikesCompiler.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent e) {
          setDefaultCompiler(CompilerConfiguration.JIKES);
        }
      }
    );
  }

  public String getDisplayName() {
    return null;
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return myJavacConfigurable.isModified() || myJikesConfigurable.isModified() || !Comparing.equal(myDefaultCompiler, myCompilerConfiguration.getDefaultCompiler());
  }

  public void apply() throws ConfigurationException {
    myJavacConfigurable.apply();
    myJikesConfigurable.apply();
    myCompilerConfiguration.setDefaultCompiler(myDefaultCompiler);
  }

  public void reset() {
    myJavacConfigurable.reset();
    myJikesConfigurable.reset();
    setDefaultCompiler(myCompilerConfiguration.getDefaultCompiler());
  }

  public void disposeUIResources() {
  }

  private void setDefaultCompiler(String compiler) {
    if(compiler == null) {
      compiler = CompilerConfiguration.JAVAC;
    }
    if(CompilerConfiguration.JAVAC.equals(compiler)) {
      myRbSetJavacCompiler.setSelected(true);
    }
    else if(CompilerConfiguration.JIKES.equals(compiler)) {
      myRbSetJikesCompiler.setSelected(true);
    }
    myDefaultCompiler = compiler;
    myCardLayout.show(myContentPanel, compiler);
    myContentPanel.revalidate();
    myContentPanel.repaint();
  }
}
