/*
 * User: anna
 * Date: 14-Feb-2008
 */
package com.intellij.application.options.editor;

import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.openapi.options.ConfigurationException;

import javax.swing.*;

public class JavaCodeFoldingOptionsProvider implements CodeFoldingOptionsProvider{
  private JCheckBox myCbCollapseImports;
  private JCheckBox myCbCollapseJavadocComments;
  private JCheckBox myCbCollapseMethodBodies;
  private JCheckBox myCbCollapseInnerClasses;
  private JCheckBox myCbCollapseAnonymousClasses;
  private JCheckBox myCbCollapseFileHeader;
  private JCheckBox myCbCollapseAnnotations;
  private JCheckBox myCbCollapseAccessors;
  private JPanel myWholePanel;

  public JComponent createComponent() {
    return myWholePanel;
  }

  public boolean isModified() {
    JavaCodeFoldingSettings codeFoldingSettings = JavaCodeFoldingSettings.getInstance();
    boolean isModified = isModified(myCbCollapseAccessors, codeFoldingSettings.isCollapseAccessors());
        isModified |= isModified(myCbCollapseImports, codeFoldingSettings.isCollapseImports());
        isModified |= isModified(myCbCollapseJavadocComments, codeFoldingSettings.isCollapseJavadocs());
        isModified |= isModified(myCbCollapseMethodBodies, codeFoldingSettings.isCollapseMethods());
        isModified |= isModified(myCbCollapseInnerClasses, codeFoldingSettings.isCollapseInnerClasses());
        isModified |= isModified(myCbCollapseAnonymousClasses, codeFoldingSettings.isCollapseAnonymousClasses());
        isModified |= isModified(myCbCollapseFileHeader, codeFoldingSettings.isCollapseFileHeader());
        isModified |= isModified(myCbCollapseAnnotations, codeFoldingSettings.isCollapseAnnotations());
    return isModified;
  }

  private static boolean isModified(final JCheckBox checkBox, final boolean state) {
    return checkBox.isSelected() != state;
  }

  public void apply() throws ConfigurationException {
    JavaCodeFoldingSettings codeFoldingSettings = JavaCodeFoldingSettings.getInstance();
    codeFoldingSettings.setCollapseImports( myCbCollapseImports.isSelected() );
    codeFoldingSettings.setCollapseJavadocs( myCbCollapseJavadocComments.isSelected() );
    codeFoldingSettings.setCollapseMethods( myCbCollapseMethodBodies.isSelected() );
    codeFoldingSettings.setCollapseAccessors( myCbCollapseAccessors.isSelected() );
    codeFoldingSettings.setCollapseInnerClasses( myCbCollapseInnerClasses.isSelected() );
    codeFoldingSettings.setCollapseAnonymousClasses( myCbCollapseAnonymousClasses.isSelected() );
    codeFoldingSettings.setCollapseFileHeader( myCbCollapseFileHeader.isSelected() );
    codeFoldingSettings.setCollapseAnnotations( myCbCollapseAnnotations.isSelected() );
  }

  public void reset() {
    JavaCodeFoldingSettings codeFoldingSettings = JavaCodeFoldingSettings.getInstance();
    myCbCollapseAccessors.setSelected(codeFoldingSettings.isCollapseAccessors());
        myCbCollapseImports.setSelected(codeFoldingSettings.isCollapseImports());
        myCbCollapseJavadocComments.setSelected(codeFoldingSettings.isCollapseJavadocs());
        myCbCollapseMethodBodies.setSelected(codeFoldingSettings.isCollapseMethods());
        myCbCollapseInnerClasses.setSelected(codeFoldingSettings.isCollapseInnerClasses());

        myCbCollapseAnonymousClasses.setSelected(codeFoldingSettings.isCollapseAnonymousClasses());
        myCbCollapseFileHeader.setSelected(codeFoldingSettings.isCollapseFileHeader());
        myCbCollapseAnnotations.setSelected(codeFoldingSettings.isCollapseAnnotations());

  }

  public void disposeUIResources() {

  }
}