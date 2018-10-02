package circlet.actions

import circlet.client.*
import circlet.client.api.*
import circlet.components.*
import circlet.platform.client.*
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.*
import kotlinx.coroutines.*
import runtime.*

class TestCircletAction : AnAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.project?.clientOrNull?.connectionStatus?.value == ConnectionStatus.CONNECTED
    }

    override fun actionPerformed(e: AnActionEvent) {


        launch(Ui) {
            val project = e.project!!
            val result = project.connection.loginModel!!.client.me.info()

            val profile = result.profile.resolve()

            val projects = project.connection.loginModel!!.client.pr.projectsByMember(profile.id)

            val prlist = projects.joinToString(", ") { it.name }

            val repositories = project.connection.loginModel!!.client.repoService.repositories()


            Notification(
                "Circlet",
                "Circlet check",
                "Hello, ${profile.englishFullName()}. Projects: $prlist, repos: ${repositories.joinToString(", ")}",
                NotificationType.INFORMATION
            ).notify(project)


            val c = service<RepositoryComponent>()
            c.repositoryList.addAll(repositories)
        }
    }
}
