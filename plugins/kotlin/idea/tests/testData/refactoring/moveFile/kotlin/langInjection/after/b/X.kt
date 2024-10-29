package b

import org.intellij.lang.annotations.Language

private fun returnInjectedText(): String {
    val scope = "smth"

    @Language("YAML")
    val injectedTxt = """
        baseProfile: myProfile
        
        inspections:
          - group: ALL
            ignore:
              - $scope
    """.trimIndent()
    return injectedTxt
}