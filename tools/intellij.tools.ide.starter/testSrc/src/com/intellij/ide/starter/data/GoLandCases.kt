package com.intellij.ide.starter.data

import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.tools.ide.starter.product.goland.GoLand

object GoLandCases : TestCaseTemplate(IdeInfo.GoLand) {
  val CliProject = withProject(
    GitHubProject.fromGithub(repoRelativeUrl = "/urfave/cli")
  )
}