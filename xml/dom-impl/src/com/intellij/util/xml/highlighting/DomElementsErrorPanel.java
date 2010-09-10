/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.util.xml.highlighting;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomChangeAdapter;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.ui.CommittablePanel;
import com.intellij.util.xml.ui.Highlightable;

import javax.swing.*;
import java.awt.*;

/**
 * User: Sergey.Vasiliev
 */
public class DomElementsErrorPanel extends JPanel implements CommittablePanel, Highlightable {
  private static final Icon ERRORS_FOUND_ICON = IconLoader.getIcon("/general/errorsInProgress.png");

  private static final int ALARM_PERIOD = 241;

  private final Project myProject;
  private final DomElement[] myDomElements;

  private final DomElementsTrafficLightRenderer myErrorStripeRenderer;
  private final DomElementAnnotationsManagerImpl myAnnotationsManager;

  private final Alarm myAlarm = new Alarm();

  public DomElementsErrorPanel(final DomElement... domElements) {
    assert domElements.length > 0;

    myDomElements = domElements;
    final DomManager domManager = domElements[0].getManager();
    myProject = domManager.getProject();
    myAnnotationsManager = (DomElementAnnotationsManagerImpl)DomElementAnnotationsManager.getInstance(myProject);

    setPreferredSize(getDimension());

    myErrorStripeRenderer = new DomElementsTrafficLightRenderer(DomUtil.getFile(domElements[0]));

    addUpdateRequest();
    domManager.addDomEventListener(new DomChangeAdapter() {
      protected void elementChanged(DomElement element) {
        addUpdateRequest();
      }
    }, this);
  }

  public void updateHighlighting() {
    updatePanel();
  }

  private boolean areValid() {
    for (final DomElement domElement : myDomElements) {
      if (!domElement.isValid()) return false;
    }
    return true;
  }

  private void updatePanel() {
    myAlarm.cancelAllRequests();

    if (!areValid()) return;

    repaint();
    setToolTipText(myErrorStripeRenderer.getTooltipMessage());

    if (!isHighlightingFinished()) {
      addUpdateRequest();
    }
  }

  private boolean isHighlightingFinished() {
    return !areValid() || myAnnotationsManager.isHighlightingFinished(myDomElements);
  }

  private void addUpdateRequest() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {

      public void run() {
        myAlarm.addRequest(new Runnable() {
          public void run() {
            if (myProject.isOpen() && !myProject.isDisposed()) {
              updatePanel();
            }
          }
        }, ALARM_PERIOD);
      }
    });
  }

  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    myErrorStripeRenderer.paint(this, g, new Rectangle(0, 0, getWidth(), getHeight()));
  }

  public void dispose() {
    myAlarm.cancelAllRequests();
  }

  public JComponent getComponent() {
    return this;
  }

  public void commit() {
  }

  public void reset() {
    updatePanel();
  }

  private static Dimension getDimension() {
    return new Dimension(ERRORS_FOUND_ICON.getIconWidth() + 2, ERRORS_FOUND_ICON.getIconHeight() + 2);
  }

  private class DomElementsTrafficLightRenderer extends TrafficLightRenderer {

    public DomElementsTrafficLightRenderer(final PsiFile xmlFile) {
      super(xmlFile.getProject(), (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(xmlFile.getProject()),
            PsiDocumentManager.getInstance(xmlFile.getProject()).getDocument(xmlFile), xmlFile);
    }

    protected DaemonCodeAnalyzerStatus getDaemonCodeAnalyzerStatus(boolean fillErrorsCount) {
      final DaemonCodeAnalyzerStatus status = super.getDaemonCodeAnalyzerStatus(fillErrorsCount);
      if (status != null && isInspectionCompleted()) {
        status.errorAnalyzingFinished = true;
      }
      return status;
    }

    @Override
    protected void fillDaemonCodeAnalyzerErrorsStatus(DaemonCodeAnalyzerStatus status,
                                                      boolean fillErrorsCount,
                                                      SeverityRegistrar severityRegistrar) {
      for (int i = 0; i < status.errorCount.length; i++) {
        final HighlightSeverity minSeverity = severityRegistrar.getSeverityByIndex(i);
        if (minSeverity == null) {
          continue;
        }

        int sum = 0;
        for (DomElement element : myDomElements) {
          final DomElementsProblemsHolder holder = myAnnotationsManager.getCachedProblemHolder(element);
          sum += (SeverityRegistrar.getInstance(getProject()).compare(minSeverity, HighlightSeverity.WARNING) >= 0 ? holder
            .getProblems(element, true, true) : holder.getProblems(element, true, minSeverity)).size();
        }
        status.errorCount[i] = sum;
      }
    }

    protected boolean isInspectionCompleted() {
      return ContainerUtil.and(myDomElements, new Condition<DomElement>() {
        public boolean value(final DomElement element) {
          return myAnnotationsManager.getHighlightStatus(element) == DomHighlightStatus.INSPECTIONS_FINISHED;
        }
      });
    }

    protected boolean isErrorAnalyzingFinished() {
      return ContainerUtil.and(myDomElements, new Condition<DomElement>() {
        public boolean value(final DomElement element) {
          return myAnnotationsManager.getHighlightStatus(element).compareTo(DomHighlightStatus.ANNOTATORS_FINISHED) >= 0;
        }
      });
    }

  }

}
