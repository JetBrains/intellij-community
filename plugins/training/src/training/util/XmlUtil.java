package training.util;

import training.keymap.KeymapUtil;
import training.ui.Message;

import javax.swing.*;
import java.util.ArrayList;

/**
 * Created by karashevich on 19/03/15.
 */
public class XmlUtil {

    private final static String SHORTCUT = "<shortcut>";

    /**
     *
     * @return null if no <shortcut> tag inside text
     */
    public static String substitution(String text, String shortcutString){
        if (text.contains(SHORTCUT)) {
            return text.replace(SHORTCUT, shortcutString);
        } else {
            return text;
        }
    }

    /**
     *
     * replaces in text all <action="actionId"> with correspondent shortcut.
     */
    public static String replacement(String text){
        String result = text;
        final String TAG = "action";

        while(result.contains("<" + TAG + "=\"")) {
            int start = result.indexOf("<" + TAG + "=\"");
            int end = result.indexOf("\">", start);

            String value = result.substring((start + 3 + TAG.length()), end);
            String replaceString = "ACTION";
            result = result.substring(0, start) + replaceString + result.substring(end + 2);
        }

        return result;
    }

    public static String replaceWithActionShortcut(String text){
        String result = text;
        final String TAG = "action";

        while(result.contains("<" + TAG + "=\"")) {
            int start = result.indexOf("<" + TAG + "=\"");
            int end = result.indexOf("\">", start);

            String value = result.substring((start + 3 + TAG.length()), end);
            final KeyStroke shortcutByActionId = KeymapUtil.INSTANCE.getShortcutByActionId(value);
            String shortcutText;
            if (shortcutByActionId == null) {
                shortcutText = value;
            } else {
                shortcutText = KeymapUtil.INSTANCE.getKeyStrokeText(shortcutByActionId);
            }

            result = result.substring(0, start) + shortcutText + result.substring(end + 2);
        }

        return result;
    }


    private static Message[] extractActions(Message[] messages){
        final String TAG = "action";

        ArrayList<Message> result = new ArrayList<>();
        for (Message message: messages) {
            if (message.isText()) {
                String parsingString = message.getText();
                while (parsingString.contains("<" + TAG + "=\"")) {
                    int start = parsingString.indexOf("<" + TAG + "=\"");
                    int end = parsingString.indexOf("\">", start);

                    String value = parsingString.substring((start + 3 + TAG.length()), end);

                    final KeyStroke shortcutByActionId = KeymapUtil.INSTANCE.getShortcutByActionId(value);
                    String shortcutText;
                    if (shortcutByActionId == null) {
                        shortcutText = value;
                    } else {
                        shortcutText = KeymapUtil.INSTANCE.getKeyStrokeText(shortcutByActionId);
                    }

                    Message mtext = new Message(parsingString.substring(0, start), Message.MessageType.TEXT_REGULAR);
                    Message maction = new Message(shortcutText, Message.MessageType.SHORTCUT);
                    result.add(mtext);
                    result.add(maction);
                    parsingString = parsingString.substring(end + 2);
                }
                if (parsingString.length() > 0) {
                    Message mendtext = new Message(parsingString, Message.MessageType.TEXT_REGULAR);
                    result.add(mendtext);
                }
            }
        }

        return result.toArray(new Message[result.size()]);
    }

    private static Message[] extractCodeFragments(Message[] messages){
        final String TAG = "code";

        ArrayList<Message> result = new ArrayList<>();
        for (Message message: messages) {
            if (message.isText()) {
                String parsingString = message.getText();
                while (parsingString.contains("<" + TAG + ">")) {
                    int start = parsingString.indexOf("<" + TAG + ">");
                    int end = parsingString.indexOf("</" + TAG + ">", start);

                    String value = parsingString.substring((start + 2 + TAG.length()), end);


                    Message msg_pre_text = new Message(parsingString.substring(0, start), Message.MessageType.TEXT_REGULAR);
                    Message msg_code = new Message(value, Message.MessageType.CODE);
                    result.add(msg_pre_text);
                    result.add(msg_code);
                    parsingString = parsingString.substring(end + 3 + TAG.length());
                }
                if (parsingString.length() > 0) {
                    Message msg_after_text = new Message(parsingString, Message.MessageType.TEXT_REGULAR);
                    result.add(msg_after_text);
                }
            } else {
                result.add(message);
            }
        }

        return result.toArray(new Message[result.size()]);
    }

    private static Message[] extractBoldFragments(Message[] messages){
        final String TAG = "bold";

        ArrayList<Message> result = new ArrayList<>();
        for (Message message: messages) {
            if (message.isText()) {
                String parsingString = message.getText();
                while (parsingString.contains("<" + TAG + ">")) {
                    int start = parsingString.indexOf("<" + TAG + ">");
                    int end = parsingString.indexOf("</" + TAG + ">", start);

                    String value = parsingString.substring((start + 2 + TAG.length()), end);


                    Message msg_pre_text = new Message(parsingString.substring(0, start), Message.MessageType.TEXT_REGULAR);
                    Message msg_code = new Message(value, Message.MessageType.TEXT_BOLD);
                    result.add(msg_pre_text);
                    result.add(msg_code);
                    parsingString = parsingString.substring(end + 3 + TAG.length());
                }
                if (parsingString.length() > 0) {
                    Message msg_after_text = new Message(parsingString, Message.MessageType.TEXT_REGULAR);
                    result.add(msg_after_text);
                }
            } else {
                result.add(message);
            }
        }

        return result.toArray(new Message[result.size()]);
    }

    private static Message[] extractLinkFragments(Message[] messages){
        final String TAG = "link";

        ArrayList<Message> result = new ArrayList<>();
        for (Message message: messages) {
            if (message.isText()) {
                String parsingString = message.getText();
                while (parsingString.contains("<" + TAG + ">")) {
                    int start = parsingString.indexOf("<" + TAG + ">");
                    int end = parsingString.indexOf("</" + TAG + ">", start);

                    String value = parsingString.substring((start + 2 + TAG.length()), end);


                    Message msg_pre_text = new Message(parsingString.substring(0, start), Message.MessageType.TEXT_REGULAR);
                    final Message msg_link = new Message(value, Message.MessageType.LINK);
                    result.add(msg_pre_text);
                    result.add(msg_link);
                    parsingString = parsingString.substring(end + 3 + TAG.length());
                }
                if (parsingString.length() > 0) {
                    Message msg_after_text = new Message(parsingString, message.getType());
                    result.add(msg_after_text);
                }
            } else {
                result.add(message);
            }
        }

        return result.toArray(new Message[result.size()]);
    }

    public static Message[] extractAll(Message[] messages){
        return extractLinkFragments(extractBoldFragments(extractCodeFragments(extractActions(messages))));
    }



    public static void main(String[] args) {
        final Message[] messages = extractAll(new Message[]{new Message("text is h<code>Some code is here</code>ere and some <bold>bold code</bold> <action=\"EditorSelectWord\"> and <action=\"EditorSelectWord\"> here", Message.MessageType.TEXT_REGULAR)});
        for (Message message : messages) {
            System.out.println(message.toString());
        }
    }

    public static String removeHtmlTags(String text){
        final int n = text.length();
        if (n > 12 && text.substring(0, 6).equals("<html>") && (text.substring(n-7, n).equals("</html>")))
            return text.substring(6, n - 7);
        else
            return text;
    }

    static String addHtmlTags(String text){
        final int n = text.length();
        if (n > 12 && text.substring(0, 6).equals("<html>") && (text.substring(n-7, n).equals("</html>")))
            return text;
        else
            return "<html>" + text + "</html>";
    }


}
