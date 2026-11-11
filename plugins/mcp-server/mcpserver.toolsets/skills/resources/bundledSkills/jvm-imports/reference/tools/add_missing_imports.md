# add_missing_imports
Write the code first and leave the imports out, then call this tool. It resolves a short name the way the editor does, so it also finds an extension function, a top-level function, and a typealias.<br/>A Java static method or a Java static field gets an `import static`. `staticMember: true` marks such a candidate, so an entry of `ambiguous` tells you which form to write.<br/>`mode` defaults to `apply`: the tool writes the files and saves them, so read such a file again before you edit it. `mode: diff` only previews the change and writes nothing.<br/>`ambiguity` defaults to `report`: a name with several candidates goes to `ambiguous` and gets no import, so pick a candidate by the context of your task. `ambiguity: best` takes the first candidate of the IDE.<br/>`optimize` defaults to false, so every name gets its own import. `optimize: true` then runs Optimize Imports with the style of the project, which can join several imports of one package into one import on demand, and also removes the unused imports and sorts the rest. Ask for it only when you want the file to match the style of the project.<br/>`diff` shows every import that the file gained. A name that the file already resolves is in no list.<br/>`unresolved` holds a name that the tool did not import. `symbol_not_found` means that the module of the file sees no declaration of the name, so check that you did not invent it, or that the module has the dependency. `fix_not_applied` means the IDE refused the change, so add that one import yourself.<br/>A file entry with a `notAnalyzedReason` was skipped. Lines are 1-based: in `diff` they point at the file on the disk, in `apply` at the file after the write.<br/>The diff stops after 200 lines and says how much it left out. `timedOut: true` means that the remaining files are missing from `items`.

## Parameters
| Name | Type | Description |
| --- | --- | --- |
| files* | array[string] | Path relative to the project root. Only a `.java` or a `.kt` file. Duplicate paths are ignored after normalization. |
| mode | string | `apply` (default) writes the files and saves them. `diff` returns the change and writes nothing. |
| ambiguity | string | `report` (default) reports a name with several candidates and imports nothing for it. `best` imports the first candidate of the IDE. |
| optimize | boolean | Whether to run Optimize Imports with the style of the project after the imports are added (default: false). It can join imports into an import on demand, and it removes the unused imports. |
| timeout | integer | Timeout in milliseconds (default: 60000) |
| projectPath | string | The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls. <br/>In the case you know only the current working directory you can use it as the project path.<br/>If you're not aware about the project path you can ask user about it. |

## Output
| Name | Type | Description |
| --- | --- | --- |
| items | array[object] | One entry per requested file, in the order of the request. |
| &nbsp;&nbsp;[].filePath* | string | The path of the file, as the request spelled it. |
| &nbsp;&nbsp;[].notAnalyzedReason | string? | Set when the file was skipped. The other fields are then empty. |
| &nbsp;&nbsp;[].diff | string? | Unified diff of every import that the file gained, including a Java `import static`. Absent when nothing changed. |
| &nbsp;&nbsp;[].applied | boolean | Whether the tool wrote the file to the disk. False in the `diff` mode. |
| &nbsp;&nbsp;[].ambiguous | array[object] | The names with several candidates. The tool imported none of them. |
| &nbsp;&nbsp;&nbsp;&nbsp;[].shortName* | string | The short name as the code spells it. |
| &nbsp;&nbsp;&nbsp;&nbsp;[].line* | integer | The 1-based line of the first reference to the name. |
| &nbsp;&nbsp;&nbsp;&nbsp;[].lineText* | string | The text of that line without its indent, so the use of the name tells which candidate fits. |
| &nbsp;&nbsp;&nbsp;&nbsp;[].candidates* | array[object] | Every import that fixes the name, best guess of the IDE first. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[].fqName* | string | The fully qualified name to import. For a static member, the class and the member. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[].source | string? | The module or the library that holds the declaration. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[].deprecated | boolean | Whether the declaration is deprecated. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[].staticMember | boolean | Whether the import is a Java `import static` of a method or a field. |
| &nbsp;&nbsp;[].unresolved | array[object] | The names that no import can fix. |
| &nbsp;&nbsp;&nbsp;&nbsp;[].shortName* | string | The short name as the code spells it. |
| &nbsp;&nbsp;&nbsp;&nbsp;[].line* | integer | The 1-based line of the first reference to the name. |
| &nbsp;&nbsp;&nbsp;&nbsp;[].reason* | string | `symbol_not_found` or `fix_not_applied`. |
| timedOut | boolean? | Indicates whether the operation was timed out. 'true' value may mean that the results may be incomplete or partial. 'false', 'null' or missing value means that the operation has not been timed out. |
| partialResultReason | string? | Set when the results may be incomplete, for example because the project was indexing. |

