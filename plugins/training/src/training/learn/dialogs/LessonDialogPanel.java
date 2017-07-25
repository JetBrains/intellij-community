package training.learn.dialogs;

import com.intellij.CommonBundle;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.util.TipUIUtil;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.DefaultKeymap;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ResourceUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import training.learn.LearnBundle;
import training.util.MyClassLoader;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;

/**
 * Created by karashevich on 15/01/16.
 */
class LessonDialogPanel extends JPanel{
    private static final int DEFAULT_WIDTH = 300;
    private static final int DEFAULT_HEIGHT = 150;
    private static final String SHORTCUT_ENTITY = "&shortcut:";

    private final JEditorPane myBrowser;
//    private final JLabel myPoweredByLabel;

    LessonDialogPanel() {
        setLayout(new BorderLayout());
//        mute tip icon
//        JLabel jlabel = new JLabel(AllIcons.General.Tip);
//        jlabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
        JLabel label1 = new JLabel(LearnBundle.INSTANCE.message("dialog.lessonDialog.title"));
        Font font = label1.getFont();
        label1.setFont(font.deriveFont(Font.PLAIN, font.getSize() + 4));
        JPanel jpanel = new JPanel();
        jpanel.setLayout(new BorderLayout());
//        jpanel.add(jlabel, BorderLayout.WEST);
        jpanel.add(label1, BorderLayout.CENTER);
        jpanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        add(jpanel, BorderLayout.NORTH);
        myBrowser = createDialogBrowser();
        JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myBrowser);
        add(scrollPane, BorderLayout.CENTER);

//        JPanel southPanel = new JPanel(new BorderLayout());
//        JCheckBox showOnStartCheckBox = new JCheckBox(IdeBundle.message("checkbox.show.tips.on.startup"), true);
//        showOnStartCheckBox.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
//        final GeneralSettings settings = GeneralSettings.getInstance();
//        showOnStartCheckBox.setSelected(settings.isShowTipsOnStartup());
//        showOnStartCheckBox.addItemListener(new ItemListener() {
//            @Override
//            public void itemStateChanged(@NotNull ItemEvent e) {
//                settings.setShowTipsOnStartup(e.getStateChange() == ItemEvent.SELECTED);
//            }
//        });
//        southPanel.add(showOnStartCheckBox, BorderLayout.WEST);

//        myPoweredByLabel = new JBLabel();
//        myPoweredByLabel.setHorizontalAlignment(SwingConstants.RIGHT);
//        myPoweredByLabel.setForeground(SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES.getFgColor());

//        southPanel.add(myPoweredByLabel, BorderLayout.EAST);
//        add(southPanel, BorderLayout.SOUTH);

    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public void setContent(String contentFileName) {
        openMessageInBrowser(contentFileName, this.myBrowser);
//        myPoweredByLabel.setText(TipUIUtil.getPoweredByText(content));
    }

    @NotNull
    private static JEditorPane createDialogBrowser() {
        JEditorPane browser = new JEditorPane();
        browser.setEditable(false);
        browser.setBackground(UIUtil.getTextFieldBackground());
        browser.addHyperlinkListener(e -> {
            if(e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                BrowserUtil.browse(e.getURL());
            }

        });
        URL resource = ResourceUtil.getResource(TipUIUtil.class, "/tips/css/", UIUtil.isUnderDarcula()?"tips_darcula.css":"tips.css");
        final StyleSheet styleSheet = UIUtil.loadStyleSheet(resource);
        HTMLEditorKit kit = new HTMLEditorKit() {
            public StyleSheet getStyleSheet() {
                return styleSheet != null?styleSheet:super.getStyleSheet();
            }
        };
        browser.setEditorKit(kit);
        return browser;
    }

    private static void openMessageInBrowser(@Nullable String messageFileName, JEditorPane browser) {
        if (messageFileName == null) return;
        try {

            final URL url = MyClassLoader.getInstance().getDialogURL(messageFileName);
//            URL url = MyClassLoader.getInstance().getResourceAsStream(messageFileName);

            if (url == null) {
                //setCantReadText(browser, tip);
                return;
            }

            StringBuffer text = new StringBuffer(ResourceUtil.loadText(url));
            updateShortcuts(text);
            updateImages(text);
            String replaced = text.toString().replace("&productName;", ApplicationNamesInfo.getInstance().getFullProductName());
            String major = ApplicationInfo.getInstance().getMajorVersion();
            replaced = replaced.replace("&majorVersion;", major);
            String minor = ApplicationInfo.getInstance().getMinorVersion();
            replaced = replaced.replace("&minorVersion;", minor);
            replaced = replaced.replace("&majorMinorVersion;", major + ("0".equals(minor) ? "" : ("." + minor)));
            replaced = replaced.replace("&settingsPath;", CommonBundle.settingsActionPath());
            replaced = replaced.replaceFirst("<link rel=\"stylesheet\".*tips\\.css\">", ""); // don't reload the styles
            if (browser.getUI() == null) {
                browser.updateUI();
            }
            adjustFontSize(((HTMLEditorKit)browser.getEditorKit()).getStyleSheet());
            browser.read(new StringReader(replaced), url);
        }
        catch (IOException e) {
//            setCantReadText(browser, tip);
        }
    }

    private static final String TIP_HTML_TEXT_TAGS = "h1, p, pre, ul";

    private static void adjustFontSize(StyleSheet styleSheet) {
        int size = (int)UIUtil.getFontSize(UIUtil.FontSize.MINI);
        styleSheet.addRule(TIP_HTML_TEXT_TAGS + " {font-size: " + size + "px;}");
    }

    private static void updateImages(StringBuffer text) {
        final boolean dark = UIUtil.isUnderDarcula();
        final boolean retina = UIUtil.isRetina();
//    if (!dark && !retina) {
//      return;
//    }

        String suffix = "";
        if (retina) suffix += "@2x";
        if (dark) suffix += "_dark";
        int index = text.indexOf("<img", 0);
        while (index != -1) {
            final int end = text.indexOf(">", index + 1);
            if (end == -1) return;
            final String img = text.substring(index, end + 1).replace('\r', ' ').replace('\n',' ');
            final int srcIndex = img.indexOf("src=");
            final int endIndex = img.indexOf(".png", srcIndex);
            if (endIndex != -1) {
                String path = img.substring(srcIndex + 5, endIndex);
                if (!path.endsWith("_dark") && !path.endsWith("@2x")) {
                    path += suffix + ".png";
//                    URL url = ResourceUtil.getResource(tipLoader, "/tips/", path);
                    URL url = MyClassLoader.getInstance().getDialogURL(path);

                    if (url != null) {
                        String newImgTag = "<img src=\"" + path + "\" ";
                        if (retina) {
                            try {
                                final BufferedImage image = ImageIO.read(url.openStream());
                                final int w = image.getWidth() / 2;
                                final int h = image.getHeight() / 2;
                                newImgTag += "width=\"" + w + "\" height=\"" + h + "\"";
                            } catch (Exception ignore) {
                                newImgTag += "width=\"400\" height=\"200\"";
                            }
                        }
                        newImgTag += "/>";
                        text.replace(index, end + 1, newImgTag);
                    }
                }
            }
            index = text.indexOf("<img", index + 1);
        }
    }

    private static void updateShortcuts(StringBuffer text) {
        int lastIndex = 0;
        while(true) {
            lastIndex = text.indexOf(SHORTCUT_ENTITY, lastIndex);
            if (lastIndex < 0) return;
            final int actionIdStart = lastIndex + SHORTCUT_ENTITY.length();
            int actionIdEnd = text.indexOf(";", actionIdStart);
            if (actionIdEnd < 0) {
                return;
            }
            final String actionId = text.substring(actionIdStart, actionIdEnd);
            String shortcutText = getShortcutText(actionId, KeymapManager.getInstance().getActiveKeymap());
            if (shortcutText == null) {
                Keymap defKeymap = KeymapManager.getInstance().getKeymap(DefaultKeymap.getInstance().getDefaultKeymapName());
                if (defKeymap != null) {
                    shortcutText = getShortcutText(actionId, defKeymap);
                    if (shortcutText != null) {
                        shortcutText += " in default keymap";
                    }
                }
            }
            if (shortcutText == null) {
                shortcutText = "<no shortcut for action " + actionId + ">";
            }
            text.replace(lastIndex, actionIdEnd + 1, shortcutText);
            lastIndex += shortcutText.length();
        }
    }

    @Nullable
    private static String getShortcutText(String actionId, Keymap keymap) {
        for (final Shortcut shortcut : keymap.getShortcuts(actionId)) {
            if (shortcut instanceof KeyboardShortcut) {
                return KeymapUtil.getShortcutText(shortcut);
            }
        }
        return null;
    }
}
