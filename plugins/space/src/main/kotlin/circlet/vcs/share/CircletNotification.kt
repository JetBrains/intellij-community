package circlet.vcs.share

import com.intellij.notification.*
import com.intellij.openapi.project.*
import com.intellij.openapi.vcs.*

object CircletNotification {
    fun showErrorWithURL(project: Project,
                         title: String,
                         prefix: String,
                         highlight: String,
                         postfix: String,
                         url: String) {
        VcsNotifier.getInstance(project).notifyError(title, prefix + "<a href='" + url + "'>" + highlight + "</a>" + postfix,
                                                     com.intellij.notification.NotificationListener.URL_OPENING_LISTENER)
    }

    fun showInfoWithURL(project: Project,
                        title: String,
                        message: String,
                        url: String) {
        VcsNotifier.getInstance(project)
            .notifyImportantInfo(title, "<a href='" + url + "'>" + message + "</a>", NotificationListener.URL_OPENING_LISTENER);
    }
}
