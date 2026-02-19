package org.jetbrains.plugins.textmate

/**
 * 'TextMateBackedFileType' is a marker interface designed for usage when a specific language is supported through a TextMate bundle,
 * but it also requires a registered FileType. If this interface is not implemented under such circumstances, the TextMateFileType
 * will be the default choice. It would consequently supersede any behavior previously defined in a basic FileType for the supporting language.
 *
 * For instance, if a plugin has registered a TextMateBackedFileType for a certain language,
 * it's assured to be suggested for installation upon opening the corresponding file in the editor.
 * Moreover, routine features like a displaying an icon for the registered language are also included.
 */
interface TextMateBackedFileType