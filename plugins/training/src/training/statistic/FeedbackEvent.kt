package training.statistic

import com.intellij.openapi.application.PermanentInstallationID

/**
 * Created by jetbrains on 03/08/16.
 */


class FeedbackEvent(feedback: Map<String, String>) {
    @Transient var recorderId = "training"
    @Transient var timestamp = System.currentTimeMillis()
    @Transient var actionType: String = "post.feedback"
    @Transient var userUid: String = PermanentInstallationID.get()
    @Transient var feedback = feedback

    override fun toString(): String {
        return "${timestamp}\t${recorderId}\t${userUid}\t${actionType}\t${map2json(feedback)}"
    }

    fun map2json(feedback: Map<String, String>): String{
        var result = StringBuilder()
        result.append("{\"feedback\":{")
        feedback.keys.forEachIndexed { i, s ->  result.append(""); result.append("${if (i == 0) "" else ", "}\"${s}\":\"${feedback.get(s)}\"")}
        result.append("}}")
        return result.toString()
    }

}