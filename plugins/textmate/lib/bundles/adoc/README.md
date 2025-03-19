# AsciiDoc support for Visual Studio Code

[![Version](https://img.shields.io/visual-studio-marketplace/v/asciidoctor.asciidoctor-vscode)](https://marketplace.visualstudio.com/items?itemName=asciidoctor.asciidoctor-vscode)
[![Installs](https://img.shields.io/visual-studio-marketplace/i/asciidoctor.asciidoctor-vscode)](https://marketplace.visualstudio.com/items?itemName=asciidoctor.asciidoctor-vscode)
[![Ratings](https://img.shields.io/visual-studio-marketplace/r/asciidoctor.asciidoctor-vscode)](https://marketplace.visualstudio.com/items?itemName=asciidoctor.asciidoctor-vscode)
[![Project chat](https://img.shields.io/badge/zulip-join_chat-brightgreen.png)](https://chat.asciidoctor.org/)

An extension that provides live preview, syntax highlighting and snippets for the AsciiDoc format using Asciidoctor.

![demo](images/simple.gif)

## How to Install

Launch Visual Studio Code "Quick Open" (`Ctrl+P`), paste the following command, and press `Enter`:

`ext install asciidoctor.asciidoctor-vscode`

Alternatively, you can use the built-in extension browser to find the _AsciiDoc_ by _asciidoctor_ extension and install it.

This extension is also available as a pre-version (alpha) in [Visual Studio Code for the Web](https://code.visualstudio.com/docs/editor/vscode-web)
and can be installed using the same procedure.

|Feature|Desktop|Web|
|--|--|--|
|Document Outline and Symbols|✔️|✔️|
|Equations (via Mathjax)|✔️|✔️ (requires security to be disabled)|
|Export as PDF|✔️|⛔|
|Kroki Integration for Diagrams|✔️|✔️|
|Paste Image |✔️|⛔|
|Save as HTML|✔️|⛔|
|Save as DocBook|✔️|⛔|
|Snippets|✔️|✔️|
|Syntax Highlighting|✔️|✔️ (requires security to be disabled)|
|Sync scrolling between the editor and the preview|✔️|✔️|

## How to Use

The extension activates automatically when opening an AsciiDoc file (.adoc, .ad, .asc, .asciidoc).

### Preview

To show the preview you can use the same commands as the Markdown extension:

* Toggle Preview - `ctrl+shift+v` (Mac: `cmd+shift+v`)
* Open Preview to the Side - `ctrl+k v` (Mac: `cmd+k v`)

The extension updates automatically the preview but it can also be forced with the _AsciiDoc: Refresh Preview_ command.

The preview supports setting AsciiDoc attributes through the `asciidoc.preview.asciidoctorAttributes` setting.

By default, the preview uses VS Code editor theme (`workbench.colorTheme`).
To use Asciidoctor default style set the `asciidoc.preview.useEditorStyle` setting to `false`.
It is possible to set your own preview stylesheet with the `asciidoc.preview.style` setting.<br/>
It is also possible to define custom templates with the `asciidoc.preview.templates` setting.<br/>
(See more details under [Extension Settings](#extension-settings))

### Export as PDF

The extension provides a quick command to export your AsciiDoc file as PDF.

* Open the command palette - `ctrl+shift+p` or `F1` (Mac: `cmd+shift+p`)
* Select _AsciiDoc: Export document as PDF_
* Choose the folder and filename for the generated PDF

By default, the PDF export feature relies on `asciidoctor-pdf`.
If you prefer to use `wkhtmltopdf`, set the `asciidoc.pdf.engine` setting to `wkhtmltopdf` and configure the `asciidoc.pdf.wkhtmltopdfCommandPath` if necessary.<br/>
(See more details under [Extension Settings](#extension-settings))

### Save as HTML

The extension provides a quick command to export your AsciiDoc file as HTML using the default Asciidoctor stylesheet.

* Open the command palette - `ctrl+shift+p` or `F1` (Mac: `cmd+shift+p`)
* Select _AsciiDoc: Save HTML document_
* The file is generated in the same folder as the source document

The shortcut key of `ctrl+alt+s` (Mac: `cmd+alt+s`) will also save the document.

### Save to Docbook

The extension provides a quick command to export your AsciiDoc file as DocBook.

* Open the command palette - `ctrl+shift+p` or `F1` (Mac: `cmd+shift+p`)
* Select _AsciiDoc: Save to DocBook_
* The file is generated in the same folder as the source document

**ℹ️ Note:** Only DocBook 5 is supported.

### Snippets

Several code snippets are provided including but not limited to: include statements, images, links, header, headings, lists, blocks, etc...

For a full list open the command palette and select _Insert Snippet_.

### Identifying the VS Code Environment

The `env-vscode` attribute is set on all output documents.
If you need to identify or handle the VS Code environment you can use an `ifdef` expression similar to the following:

```asciidoc
ifdef::env-vscode[]
This is for vscode only
endif::[]
```

### Diagram Integration

This extension supports a wide range of diagrams from BPMN to Graphviz to PlantUML and Vega graphs using [Kroki](https://kroki.io/)
and [asciidoctor-kroki](https://github.com/Mogztter/asciidoctor-kroki).

You can [see the full range](https://kroki.io/#support) on the Kroki website.

Note that this extension will send graph information to <https://kroki.io>.
If this is an issue it is also possible to use your own Kroki instance (see [the instructions](https://github.com/Mogztter/asciidoctor-kroki#using-your-own-kroki) for further information).

To enable diagram support, set the `asciidoc.extensions.enableKroki` setting to `true`.

**❗ Important:** Please note that `:kroki-fetch-diagram:` is currently not supported because it relies on `unxhr` which does not work in VS Code (https://github.com/ggrossetie/unxhr/issues/98).

### Use Asciidoctor.js extensions

When using the preview, the VS Code extension can load and register Asciidoctor.js extensions.

By convention, extensions must be located in `.asciidoctor/lib` (at the root of your workspace).
The VS Code extension will recursively load all files with the extension `.js` as Asciidoctor.js extensions.
For instance, the following files will be loaded: `.asciidoctor/lib/emoji.js`, `.asciidoctor/lib/emoji/index.js` and `.asciidoctor/lib/foo/bar/baz.js`.

To use an Asciidoctor.js extension, you should enable the feature by checking "Enable Asciidoctor.js extensions registration" in the extension settings.
The first time, you will also need to confirm that you trust the authors of the Asciidoctor.js extensions located in _.asciidoctor/lib_.

![Asciidoctor.js extensions trust confirmation message](images/asciidoctor-vscode-trust-exts.png)

**❗ Important:** This feature will execute JavaScript code and should not be enabled if you don't fully trust the authors of the Asciidoctor.js extensions.

**💡 Tip:** You can always update the trust mode using the command "Manage Asciidoctor.js Extensions Trust Mode".

You can create a new extension by creating a JavaScript file in the `.asciidoctor/lib` directory or use an existing one.
Here's an example of how to use the [asciidoctor-emoji](https://github.com/mogztter/asciidoctor-emoji) extension:

1. Install the npm package in the workspace directory:

    ```shell
    npm i asciidoctor-emoji
    ```

2. Create a file a JavaScript file in _.asciidoctor/lib_ with the following content:

    ```javascript
    module.exports = require('asciidoctor-emoji')
    ```

3. Enjoy :tada:

![Asciidoctor.js Emoji extension enabled!](images/asciidoctor-vscode-emoji-ext.png)

### Asciidoctor Config File

To provide a common set of variables when rendering the preview, the extension reads an `.asciidoctorconfig` or `.asciidoctorconfig.adoc` configuration file. Use this to optimize the preview when the project contains a document that is split out to multiple include-files.

It is inspired by the implementation provided in [IntelliJ AsciiDoc Plugin](https://intellij-asciidoc-plugin.ahus1.de/docs/users-guide/features/advanced/asciidoctorconfig-file.html) and reused in [Eclipse AsciiDoc plugin](https://github.com/de-jcup/eclipse-asciidoctor-editor/wiki/Asciidoctor-configfiles).

## Extension Settings

This extension contributes the following settings:

### Preview

| Name                                           | Description                                                                                                                        | Default Value                                                                                                             |
|:-----------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------|
| `asciidoc.preview.asciidoctorAttribute`        | Asciidoctor attributes used in the preview (object of `{string: string}`).                                                         | `{}`                                                                                                                      |
| `asciidoc.preview.refreshInterval`             | Interval in milliseconds between two consecutive updates of the preview. The value 0 means it will only update the preview on save. | `2000`                                                                                                                    |
| `asciidoc.preview.style`                       | An URL or a local path to CSS style sheets to use from the preview.                                                                |                                                                                                                           |
| `asciidoc.preview.useEditorStyle`              | Use VS Code editor style instead of the default Asciidoctor style.                                                                 |                                                                                                                           |
| `asciidoc.preview.fontFamily`                  | Control the font family used in the preview.                                                                                       | `"-apple-system, BlinkMacSystemFont, 'Segoe WPC', 'Segoe UI', 'HelveticaNeue-Light', 'Ubuntu', 'Droid Sans', sans-serif"` |
| `asciidoc.preview.fontSize`                    | Control the font size in pixels used in the preview.                                                                               | `14`                                                                                                                      |
| `asciidoc.preview.lineHeight`                  | Control the line height used in the preview.                                                                                       | `1.6`                                                                                                                     |
| `asciidoc.preview.scrollPreviewWithEditor`     | When the preview is scrolled, update the view of the editor.                                                                       | `true`                                                                                                                    |
| `asciidoc.preview.scrollEditorWithPreview`     | When the editor is scrolled, update the view of the preview.                                                                       | `true`                                                                                                                    |
| `asciidoc.preview.markEditorSelection`         | Mark the current editor selection in the preview.                                                                                  | `true`                                                                                                                    |
| `asciidoc.preview.doubleClickToSwitchToEditor` | Double click in the preview to switch to the editor.                                                                               | `true`                                                                                                                    |
| `asciidoc.preview.preservePreviewWhenHidden` | Keep the AsciiDoc preview in memory when it's hidden so that it reloads faster, at the expense of increased memory use. | `false` |
| `asciidoc.preview.openLinksToAsciidocFiles`    | Control how links to other AsciiDoc files in the preview should be opened. Possible values: `"inPreview"`, `"inEditor"`.           | `"inPreview"`                                                                                                             |

### PDF

| Name                                     | Description                                                                                                                                                                                                                                                                                                                                                     | Default Value                   |
|:-----------------------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:--------------------------------|
| `asciidoc.pdf.engine`                    | Control the PDF engine used to export as PDF. Possible values: `"asciidoctor-pdf"`, `"wkhtmltopdf"`.                                                                                                                                                                                                                                                            | `"asciidoctor-pdf"`             |
| `asciidoc.pdf.asciidoctorPdfCommandPath` | External `asciidoctor-pdf` command to execute. It accepts a full path to the binary, for instance: `/path/to/asciidoctor-pdf`.                                                                                                                                                                                                                                  | `"bundle exec asciidoctor-pdf"` |
| `asciidoc.pdf.asciidoctorPdfCommandArgs` | List of arguments, for instance: `-a`, `pdf-themesdir=resources/themes`, `-a`, `pdf-theme=basic`. Please note that the argument key and value should be added separately (i.e., two items). By default, it passes the following arguments: `--quiet` and `--base-dir` with the full directory path to the AsciiDoc document.                                    | `[]`                            |
| `asciidoc.pdf.wkhtmltopdfCommandPath`    | External `wkhtmltopdf` command to execute. It accepts a full path to the binary, for instance: `/path/to/wkhtmltopdf`. If the value is empty, use either `wkhtmltopdf` on Linux/macOS or `wkhtmltopdf.exe` on Windows.                                                                                                                                          | `""`                            |
| `asciidoc.pdf.wkhtmltopdfCommandArgs`    | List of arguments, for instance: `--orientation`, `Landscape`. Please note that the argument key and value should be added separately (i.e., two items). By default, it passes the following arguments: `--enable-local-file-access`, `--encoding`, `utf-8`, `--javascript-delay`, `1000`, `--footer-center` (if enabled) and `cover` (if it has a cover page). | `[]`                            |

### Extensions

| Name                                              | Description                                                                                     | Default Value |
|:--------------------------------------------------|:------------------------------------------------------------------------------------------------|:--------------|
| `asciidoc.extensions.enableKroki`                 | Enable Kroki extension to generate diagrams.                                                    | `false`       |
| `asciidoc.extensions.registerWorkspaceExtensions` | Enables Asciidoctor.js extensions registration from the workspace directory `.asciidoctor/lib`. | `false`       |

### General

| Name                                       | Description                                                             | Default Value |
|:-------------------------------------------|:------------------------------------------------------------------------|:--------------|
| `asciidoc.useWorkspaceRootAsBaseDirectory` | When in a workspace, use the workspace root path as the base directory. | `false`       |

### Debug

| Name                                    | Description                                                                     | Default Value |
|:----------------------------------------|:--------------------------------------------------------------------------------|:--------------|
| `asciidoc.debug.trace`                  | Enable debug logging for this extension. Possible values: `"off"`, `"verbose"`. | `"off"`       |
| `asciidoc.debug.enableErrorDiagnostics` | Provide error diagnostics.                                                      | `true`        |

## Build and Install from Source

### Manual

```shell
git clone https://github.com/asciidoctor/asciidoctor-vscode
cd asciidoctor-vscode
npm install
npm run package
code --install-extension *.vsix
```

## Issues

If you encounter any problems with the extension and cannot find the solution yourself, please open an issue in the dedicated GitHub
page: [asciidoctor-vscode/issues](https://github.com/asciidoctor/asciidoctor-vscode/issues).

Before opening an issue, please make sure that it is not a duplicate. Your problem may have already been brought up by another user and been
solved: [asciidoctor-vscode/issues all](https://github.com/asciidoctor/asciidoctor-vscode/issues?utf8=%E2%9C%93&q=).

When you do open an issue, remember to include the following information:

1. Description of the issue
2. VSCode version, OS (_Help -> About_) and extension version
3. Steps to reproduce the issue<br/>
   **IMPORTANT**: We cannot solve the issue if you do not explain how you encountered it
4. If the problem occurs only with a specific file, attach it, together with any screenshot that might better show what the issue is.

If your issue only appeared after updating to a new version of the extension, you can roll back to a previous one via the extensions browser. Click on the small gear icon beside
the AsciiDoc extension, then select _Install Another Version..._. A selection menu will appear allowing you to select which version you want to install.

## Contributing

To contribute simply clone the repository and then commit your changes. When you do a pull request please clearly highlight what you changed in the pull comment.

Do not update the extension version or changelog, it will be done by the maintainers when a new version is released.

If you want to update the readme, you are free to fix typos, errors, and add or improve descriptions; but, if you have a style change in mind please use an issue (or specific pull
request) so that it can be discussed.

## Credits

* [AsciiDoc](http://asciidoc.org/) by Stuart Rackham
* [Asciidoctor](https://asciidoctor.org/) organization for the language flavor
* [Asciidoctor.js](https://asciidoctor.org/docs/asciidoctor.js/) for the preview
* [Asciidoctor PDF](https://asciidoctor.org/docs/asciidoctor-pdf/) for the _Export to PDF_ function
* [wkhtmltopdf](https://wkhtmltopdf.org/) for the _Export to PDF_ function
* [Each and every contributor](https://github.com/asciidoctor/asciidoctor-vscode/graphs/contributors) to this extension.
