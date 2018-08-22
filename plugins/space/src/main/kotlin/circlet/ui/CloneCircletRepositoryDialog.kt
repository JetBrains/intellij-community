package circlet.ui

import com.intellij.dvcs.*
import com.intellij.dvcs.ui.*
import com.intellij.openapi.project.*

class CloneCircletRepositoryDialog(p: Project) : CloneDvcsDialog(p, "HHEEE", ":Waa") {
    override fun test(url: String): TestResult {
        return TestResult(null)
    }

    override fun getRememberedInputs(): DvcsRememberedInputs {
        return DvcsRememberedInputs()
    }
}
