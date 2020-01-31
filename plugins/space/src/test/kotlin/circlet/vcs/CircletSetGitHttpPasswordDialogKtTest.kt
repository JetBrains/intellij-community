package circlet.vcs

import org.junit.Test
import kotlin.test.*

internal class CircletSetGitHttpPasswordDialogKtTest {

    @Test
    fun getGitUrlHost() {
        assertEquals("http://git.jetbrains.team", getGitUrlHost("https://git.jetbrains.team/repo.git"))
        assertEquals("http://git.jetbrains.space", getGitUrlHost("https://git.jetbrains.space/org/repo.git"))
    }
}
