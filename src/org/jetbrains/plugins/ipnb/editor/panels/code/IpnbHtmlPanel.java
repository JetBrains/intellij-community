package org.jetbrains.plugins.ipnb.editor.panels.code;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbHtmlOutputCell;

import javax.swing.*;

public class IpnbHtmlPanel extends IpnbCodeOutputPanel<IpnbHtmlOutputCell> {
  public IpnbHtmlPanel(@NotNull final IpnbHtmlOutputCell cell) {
    super(cell);
  }

  @Override
  protected JComponent createViewPanel() {
    Platform.setImplicitExit(false);
    final JFXPanel javafxPanel = new JFXPanel();
    final StringBuilder text = new StringBuilder("<html>");
    for (String html : myCell.getHtmls()) {
      html = html.replace("\"", "'");
      text.append(html);
    }
    text.append("</html>");
    Platform.runLater(new Runnable() {
      @Override
      public void run() {

        BorderPane borderPane = new BorderPane();
        WebView webComponent = new WebView();

        webComponent.getEngine().loadContent(text.toString());

        borderPane.setCenter(webComponent);
        Scene scene = new Scene(borderPane, 450, 450);
        javafxPanel.setScene(scene);
      }
    });

    return javafxPanel;
  }
}
