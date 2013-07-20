package org.editorconfig.plugincomponents;

import com.intellij.util.messages.Topic;

public class DoneSavingTopic {
    public static final Topic<DoneSavingListener> DONE_SAVING = 
            new Topic<DoneSavingListener>("Done saving", DoneSavingListener.class);
}
