package training.learn.dialogs;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.StateRestoringCheckBox;
import org.jetbrains.annotations.NotNull;
import training.lang.LangManager;
import training.learn.LearnBundle;

import javax.swing.*;
import java.awt.*;

/**
 * Created by karashevich on 09/09/15.
 */
public class NoSdkProblemDialog extends DialogWrapper {
    private final Project myProject;
    private final String necessarySdkVersion;

    private StateRestoringCheckBox myCbOpenProjectSdkPreferences;


    public NoSdkProblemDialog(Project project, @NotNull String sdkVersion) {
        super(project, true);
        myProject = project;
        necessarySdkVersion = sdkVersion;
        setTitle(LearnBundle.INSTANCE.message("dialog.noSdk.title"));
        init();
    }


    @Override
    @NotNull
    protected Action[] createActions() {
        return new Action[]{getOKAction(), getCancelAction()};
    }

    @Override
    protected JComponent createNorthPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        final GridBagConstraints gbc = new GridBagConstraints();

        final String warningMessage = LearnBundle.INSTANCE.message("dialog.noSdk.message", LangManager.Companion.getInstance().getLanguageDisplayName());

        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.weighty = 1;
        gbc.weightx = 1;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(warningMessage), gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        myCbOpenProjectSdkPreferences = new StateRestoringCheckBox();
        myCbOpenProjectSdkPreferences.setText(LearnBundle.INSTANCE.message("dialog.noSdk.checkbox"));
        panel.add(myCbOpenProjectSdkPreferences, gbc);


        myCbOpenProjectSdkPreferences.setSelected(true);

        return panel;
    }



    @Override
    protected JComponent createCenterPanel() {
        return null;
    }


    @Override
    protected void doOKAction() {
        if (DumbService.isDumb(myProject)) {
            Messages.showMessageDialog(myProject, "Changing Project SDK is not available while indexing is in progress", "Indexing", null);
            return;
        }

        if (myCbOpenProjectSdkPreferences != null && myCbOpenProjectSdkPreferences.isSelected()) {
            ProjectSettingsService.getInstance(myProject).chooseAndSetSdk();
            super.doOKAction();
        }

    }

}
