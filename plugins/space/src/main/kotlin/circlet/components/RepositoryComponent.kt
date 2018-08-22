package circlet.components

import circlet.platform.api.*
import com.intellij.openapi.components.*

data class RepositoryComponentState(val repositories: List<String>)

@State(
    name = "RepositoryComponent",
    storages = [Storage(value = "CircletRepository.xml", roamingType = RoamingType.DISABLED)]
)
class RepositoryComponent : PersistentStateComponent<RepositoryComponentState>{

    val repositoryList = arrayListOf<String>()

    override fun loadState(state: RepositoryComponentState) {
        repositoryList.run {
            clear()
            addAll(state.repositories)
        }
    }

    override fun getState(): RepositoryComponentState {
        return RepositoryComponentState(repositoryList)
    }

}
