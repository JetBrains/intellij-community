# rename_refactoring
The IDE resolves the symbol through the PSI, so it also updates a reference that a text search cannot<br/>find. Always prefer this tool over a text replacement.<br/><br/>Point at the symbol with ONE of these, in order of preference: contextSnippet, then line and column.<br/>With neither of them, the file must declare one symbol with this name. symbolName is always required.<br/>A position that resolves to another name refuses the rename.<br/><br/>targetIndex is not a third way to point at the symbol. It picks from a candidates list an earlier call<br/>returned, so repeat that call with every argument it had, and add targetIndex.<br/><br/>Read `applied`. It is the only success signal. Nothing is written in part, except after<br/>error.kind=write_failed, and its hint says what was written.<br/>ok=false with candidates asks you to pick one with targetIndex. ok=false with conflicts asks for another<br/>newName. ok=false with error tells you in `hint` what to correct.<br/><br/>applyAutomaticRenamers renames what the IDE renames along with the symbol, such as a variable named after a renamed<br/>class, each kind as the settings of the IDE enable it. A library or compiled symbol cannot be renamed.<br/><br/>By default, the rename changes no comment, no string literal and no plain-text occurrence of the name.<br/>Set searchInComments or searchInText to change them too.

## Parameters
| Name | Type | Description |
| --- | --- | --- |
| pathInProject* | string | Path relative to the project root |
| symbolName* | string | Exact, case-sensitive name of the symbol to rename. |
| newName* | string | New name for the symbol. It must be a valid identifier for the symbol's language. |
| contextSnippet | string | Optional. Exact fragment of the file that shows the symbol. It must match the file once, and contain symbolName. |
| line | integer | Optional. 1-based line of the symbol. Copy it from search_symbol output. Needs column too. |
| column | integer | Optional. 1-based column of the symbol. Copy it from search_symbol output. Needs line too. |
| targetIndex | integer | Optional. 1-based pick from the candidates list an earlier call returned. Repeat every other argument of that call. |
| preview | boolean | Optional. Analyze only: report affects and conflicts, and write nothing. The default is false. |
| applyAutomaticRenamers | boolean | Optional. Also rename what the IDE renames along with the symbol. The default is true. |
| searchInComments | boolean | Optional. Also rename the name in comments and string literals. The default is false. |
| searchInText | boolean | Optional. Also rename the name in plain-text occurrences, such as non-code files. The default is false. |
| projectPath | string | The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls. <br/>In the case you know only the current working directory you can use it as the project path.<br/>If you're not aware about the project path you can ask user about it. |

## Output
| Name | Type | Description |
| --- | --- | --- |
| ok* | boolean |  |
| applied* | boolean |  |
| resolvedSymbol | object? |  |
| &nbsp;&nbsp;name | string? |  |
| &nbsp;&nbsp;declarationText* | string |  |
| &nbsp;&nbsp;declarationFile | string? |  |
| &nbsp;&nbsp;declarationLine | integer? |  |
| &nbsp;&nbsp;language | string? |  |
| affects | object? |  |
| &nbsp;&nbsp;files* | integer |  |
| &nbsp;&nbsp;usages* | integer |  |
| renamedFile | object? |  |
| &nbsp;&nbsp;previousPath* | string |  |
| &nbsp;&nbsp;path* | string |  |
| candidates | array? |  |
| &nbsp;&nbsp;[].targetIndex* | integer |  |
| &nbsp;&nbsp;[].kind | string? |  |
| &nbsp;&nbsp;[].symbol | object? |  |
| &nbsp;&nbsp;&nbsp;&nbsp;name | string? |  |
| &nbsp;&nbsp;&nbsp;&nbsp;declarationText* | string |  |
| &nbsp;&nbsp;&nbsp;&nbsp;declarationFile | string? |  |
| &nbsp;&nbsp;&nbsp;&nbsp;declarationLine | integer? |  |
| &nbsp;&nbsp;&nbsp;&nbsp;language | string? |  |
| conflicts | array? |  |
| &nbsp;&nbsp;[].description* | string |  |
| &nbsp;&nbsp;[].filePath | string? |  |
| error | object? |  |
| &nbsp;&nbsp;kind* | string |  |
| &nbsp;&nbsp;hint* | string |  |
| note | string? |  |
| partialResultReason | string? |  |

