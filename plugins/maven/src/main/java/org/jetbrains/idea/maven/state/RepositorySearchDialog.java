package org.jetbrains.idea.maven.state;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.Alarm;
import org.jetbrains.idea.maven.repo.RepositoryIndex;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.io.IOException;
import java.util.List;

public class RepositorySearchDialog extends DialogWrapper {
  private Alarm myAlarm = new Alarm();

  private JPanel myMainPanel;
  private JTextField mySearchField;
  private JList myResultsList;

  private RepositoryIndex myIndex = new RepositoryIndex("C:\\temp\\maven-central-index", null);

  public RepositorySearchDialog(Project project) {
    super(project, true);

    configComponents();

    setTitle("Hehe");
    init();
  }

  private void configComponents() {
    mySearchField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        scheduleSearch();
      }
    });
  }

  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  private void scheduleSearch() {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new Runnable() {
      public void run() {
        doSearch();
      }
    }, 500);
  }

  private void doSearch() {
    try {
      final List<String> result = myIndex.search(mySearchField.getText());
      final AbstractListModel model = new AbstractListModel() {
        public int getSize() {
          return result.size();
        }

        public Object getElementAt(int index) {
          return result.get(index);
        }
      };

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          myResultsList.setModel(model);
        }
      });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
