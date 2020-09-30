// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.configurable.VcsOptionsTopHitProviderBase
import com.intellij.ui.layout.*


private val svnOptionGroupName get() = SvnVcs.VCS_DISPLAY_NAME
private fun configuration(project: Project) = SvnConfiguration.getInstance(project)

// @formatter:off
private fun cdCheckMergeInfo(project: Project): CheckboxDescriptor =                  CheckboxDescriptor(SvnBundle.message("settings.check.mergeinfo"), PropertyBinding({ configuration(project).isCheckNestedForQuickMerge }, { configuration(project).isCheckNestedForQuickMerge = it }), groupName = svnOptionGroupName)
private fun cdShowMergeSource(project: Project): CheckboxDescriptor =                 CheckboxDescriptor(SvnBundle.message("annotation.show.merge.sources.default.text"), PropertyBinding({ configuration(project).isShowMergeSourcesInAnnotate }, { configuration(project).isShowMergeSourcesInAnnotate = it }), groupName = svnOptionGroupName)
private fun cdIgnoreWhitespacesInAnnotations(project: Project): CheckboxDescriptor =  CheckboxDescriptor(SvnBundle.message("svn.option.ignore.whitespace.in.annotate"), PropertyBinding({ configuration(project).isIgnoreSpacesInAnnotate }, { configuration(project).isIgnoreSpacesInAnnotate = it }), groupName = svnOptionGroupName)
private fun cdUseGeneralProxy(project: Project): CheckboxDescriptor =                 CheckboxDescriptor(SvnBundle.message("use.idea.proxy.as.default", ApplicationNamesInfo.getInstance().productName), PropertyBinding({ configuration(project).isUseDefaultProxy }, { configuration(project).isUseDefaultProxy = it }), groupName = svnOptionGroupName)
// @formatter:on

internal class SvnOptionsTopHitProvider : VcsOptionsTopHitProviderBase() {
  override fun getId(): String {
    return "vcs"
  }

  override fun getOptions(project: Project): Collection<OptionDescription> {
    if (isEnabled(project, SvnVcs.getKey())) {
      return listOf(
        cdCheckMergeInfo(project),
        cdShowMergeSource(project),
        cdIgnoreWhitespacesInAnnotations(project),
        cdUseGeneralProxy(project)
      ).map(CheckboxDescriptor::asOptionDescriptor)
    }
    return emptyList()
  }
}