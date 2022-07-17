/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.json

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler
import org.toml.ide.experiments.TomlExperiments
import org.toml.lang.psi.TomlFileType

class TomlJsonSchemaEnabler : JsonSchemaEnabler {
    override fun isEnabledForFile(file: VirtualFile, project: Project?): Boolean {
        if (!TomlExperiments.isJsonSchemaEnabled) return false

        return file.fileType is TomlFileType
    }
}
