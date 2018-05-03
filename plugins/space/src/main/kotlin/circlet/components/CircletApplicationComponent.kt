package circlet.components

import circlet.runtime.*
import circlet.utils.*
import com.intellij.openapi.components.*
import runtime.*


class CircletApplicationComponent : ApplicationComponent {
    init {
        mutableUiDispatch = ApplicationDispatcher(application)
    }
}
