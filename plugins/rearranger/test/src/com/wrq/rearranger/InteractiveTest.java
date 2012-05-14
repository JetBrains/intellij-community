/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.wrq.rearranger.configuration.RearrangerSettingsPanel;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.settings.attributeGroups.IPrioritizableRule;
import com.wrq.rearranger.settings.attributeGroups.IRule;
import com.wrq.rearranger.util.Constraints;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ListIterator;

/**
 * @author davek
 *         Date: Mar 30, 2004
 */
public class InteractiveTest
  extends LightCodeInsightTestCase
{
  private RearrangerSettings rs;
  public static final String DEFAULT_CONFIGURATION_ROOT = ".."; // up one level from test
  public static final String DEFAULT_CONFIGURATION      = DEFAULT_CONFIGURATION_ROOT + "/src/com/wrq/rearranger/defaultConfiguration.xml";

  @Override
  protected String getTestDataPath() {
    return "testData"; // run unit tests with effective working directory of "C:\Rearranger\test"
  }

  protected final void setUp() throws Exception {
    super.setUp();
    Logger logger = Logger.getLogger("com.wrq.rearranger");
//        logger.setAdditivity(false);
//        logger.addAppender(new ConsoleAppender(new PatternLayout("[%7r] %6p - %30.30c - %m \n")));
//        logger.setLevel(Level.DEBUG);
    logger.setLevel(Level.INFO);
    rs = new RearrangerSettings();
    rs.setAskBeforeRearranging(true);
    rs.setRearrangeInnerClasses(true);
  }

  public final void testLiveRearrangerDialog() throws Exception {
    configureByFile("/com/wrq/rearranger/RearrangementTest28.java");
    final PsiFile file = getFile();
    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
    final LiveRearrangerActionHandler rah = new LiveRearrangerActionHandler();
    rah.liveRearrangeDocument(getProject(), file, rs, doc, 0);
    super.checkResultByFile("/com/wrq/rearranger/RearrangementTest28.java");
  }

  public final void testSettingsPanel() throws Exception {
    Rearranger rearranger = new Rearranger();
    SAXBuilder builder = new SAXBuilder();
    org.jdom.Document doc = null;
    try {
      FileInputStream f = new FileInputStream(new File(DEFAULT_CONFIGURATION));
      doc = builder.build(f);
      f.close();
    }
    catch (JDOMException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }
    catch (FileNotFoundException e) {
      File f = new File(DEFAULT_CONFIGURATION);
      System.out.println(f.getAbsolutePath());
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }
    catch (IOException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }
    Element appElement = doc.getRootElement();
    java.util.List componentList = appElement.getChildren();
    Element rearrangerElement = null;
    if (appElement.getName().equals("component") &&
        appElement.getAttributeValue("name").equals(Rearranger.COMPONENT_NAME))
    {
      rearrangerElement = appElement;
    }
    else {
      ListIterator li = componentList.listIterator();
      while (li.hasNext())
//            for (Element e : componentList)
      {
        Element e = (Element)li.next();
        if (e.getAttributeValue("name").equals(Rearranger.COMPONENT_NAME)) {
          rearrangerElement = e;
          break;
        }
      }
    }
    if (rearrangerElement != null) {
      rearranger.readExternal(rearrangerElement);
    }
    final JDialog frame = new JDialog((Frame)null, "SwingApplication");
    final Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightedLastRow();
    RearrangerSettings settings = rearranger.getSettings();
    final RearrangerSettingsPanel object = new RearrangerSettingsPanel(settings);
    frame.getContentPane().setLayout(new GridBagLayout());
    frame.getContentPane().add(object, constraints.weightedLastCol());
    //Finish setting up the frame, and show it.
    frame.pack();
    frame.setResizable(true);
    frame.setModal(true);
    frame.setVisible(true);
    final RearrangerSettings rs = object.settings.deepCopy();
    assertTrue("Settings are unequal", rs.equals(object.settings));
    System.out.println("Class Order");
    ListIterator li;
    for (Object o : object.settings.getClassOrderAttributeList()) {
      if (o instanceof IPrioritizableRule) {
        System.out.println(o + ", pri=" + ((IRule)o).getPriority());
      }
      else {
        System.out.println(o);
      }
    }
    System.out.println("Item order");
    for (Object o : object.settings.getItemOrderAttributeList()) {
      if (o instanceof IPrioritizableRule) {
        System.out.println(o + ", pri=" + ((IRule)o).getPriority());
      }
      else {
        System.out.println(o);
      }
    }
    object.checkCommentsAgainstGlobalPattern(object.settings);
  }
}
