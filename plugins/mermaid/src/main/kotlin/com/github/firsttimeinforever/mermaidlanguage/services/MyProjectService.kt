package com.github.firsttimeinforever.mermaidlanguage.services

import com.intellij.openapi.project.Project
import com.github.firsttimeinforever.mermaidlanguage.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
