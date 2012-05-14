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

import com.intellij.testFramework.SkipInHeadlessEnvironment;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.wrq.rearranger.settings.RearrangerSettings;

/**
 * @author davek
 *         Date: Mar 30, 2004
 */
@SkipInHeadlessEnvironment
public class InteractiveTest
  extends LightCodeInsightFixtureTestCase
{
  public static final String DEFAULT_CONFIGURATION_ROOT = ".."; // up one level from test
  public static final String DEFAULT_CONFIGURATION      = DEFAULT_CONFIGURATION_ROOT + "/src/com/wrq/rearranger/defaultConfiguration.xml";
  private RearrangerSettings mySettings;

  @Override
  protected String getBasePath() {
    return "/plugins/rearranger/test/testData/com/wrq/rearranger";
  }

  protected final void setUp() throws Exception {
    super.setUp();
    
    mySettings = new RearrangerSettings();
    mySettings.setAskBeforeRearranging(true);
    mySettings.setRearrangeInnerClasses(true);
  }

  public final void testLiveRearrangerDialog() throws Exception {
    //configureByFile("/com/wrq/rearranger/RearrangementTest28.java");
    //final PsiFile file = getFile();
    //final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
    //final LiveRearrangerActionHandler rah = new LiveRearrangerActionHandler();
    //rah.liveRearrangeDocument(getProject(), file, mySettings, doc, 0);
    //super.checkResultByFile("/com/wrq/rearranger/RearrangementTest28.java");
  }

  //public final void testSettingsPanel() throws Exception {
  //  Rearranger rearranger = new Rearranger();
  //  SAXBuilder builder = new SAXBuilder();
  //  org.jdom.Document doc = null;
  //  FileInputStream in = new FileInputStream(new File(DEFAULT_CONFIGURATION));
  //  try {
  //    doc = builder.build(in);
  //  }
  //  catch (JDOMException e) {
  //    e.printStackTrace();
  //  }
  //  catch (FileNotFoundException e) {
  //    File f = new File(DEFAULT_CONFIGURATION);
  //    System.out.println(f.getAbsolutePath());
  //    e.printStackTrace();
  //  }
  //  catch (IOException e) {
  //    e.printStackTrace();
  //  }
  //  finally {
  //    in.close();
  //  }
  //  
  //  assert doc != null;
  //  Element appElement = doc.getRootElement();
  //  java.util.List componentList = appElement.getChildren();
  //  Element rearrangerElement = null;
  //  if (appElement.getName().equals("component") &&
  //      appElement.getAttributeValue("name").equals(Rearranger.COMPONENT_NAME))
  //  {
  //    rearrangerElement = appElement;
  //  }
  //  else {
  //    for (Object aComponentList : componentList) {
  //      Element e = (Element)aComponentList;
  //      if (e.getAttributeValue("name").equals(Rearranger.COMPONENT_NAME)) {
  //        rearrangerElement = e;
  //        break;
  //      }
  //    }
  //  }
  //  if (rearrangerElement != null) {
  //    rearranger.readExternal(rearrangerElement);
  //  }
  //  final JDialog frame = new JDialog((Frame)null, "SwingApplication");
  //  final Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
  //  constraints.fill = GridBagConstraints.BOTH;
  //  constraints.weightedLastRow();
  //  RearrangerSettings settings = rearranger.getSettings();
  //  final RearrangerSettingsPanel object = new RearrangerSettingsPanel(settings);
  //  frame.getContentPane().setLayout(new GridBagLayout());
  //  frame.getContentPane().add(object, constraints.weightedLastCol());
  //  //Finish setting up the frame, and show it.
  //  frame.pack();
  //  frame.setResizable(true);
  //  frame.setModal(true);
  //  frame.setVisible(true);
  //  final RearrangerSettings rs = object.settings.deepCopy();
  //  assertTrue("Settings are unequal", rs.equals(object.settings));
  //  System.out.println("Class Order");
  //  for (Object o : object.settings.getClassOrderAttributeList()) {
  //    if (o instanceof Rule && o instanceof PrioritizedRule) {
  //      System.out.println(o + ", pri=" + ((Rule)o).getPriority());
  //    }
  //    else {
  //      System.out.println(o);
  //    }
  //  }
  //  System.out.println("Item order");
  //  for (Object o : object.settings.getItemOrderAttributeList()) {
  //    if (o instanceof Rule && o instanceof PrioritizedRule) {
  //      System.out.println(o + ", pri=" + ((Rule)o).getPriority());
  //    }
  //    else {
  //      System.out.println(o);
  //    }
  //  }
  //  object.checkCommentsAgainstGlobalPattern(object.settings);
  //}
}
