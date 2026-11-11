# lint_files
Use this tool to lint several files after editing them.<br/>Returns per-file problems with severity, description, and location information.<br/>Batch responses may include file entries with `timedOut: true` and empty `problems` when individual files exceed the available budget.<br/>File entries with a `notAnalyzedReason` indicate files that could not be analyzed (e.g., outside project content roots, excluded, or unsupported file type).<br/>Top-level `more: true` means the batch is incomplete.<br/>`min_severity` must be `warning`, `strong_warning`, or `error`; defaults to `warning`.<br/>Note: Only analyzes files within the project directory.<br/>Note: Lines and Columns are 1-based.

## Parameters
| Name | Type | Description |
| --- | --- | --- |
| files* | array[string] | List of project-relative files to analyze. Duplicate paths are ignored after normalization. |
| min_severity | string | Minimum severity to include: `warning` (includes weak warnings), `strong_warning`, or `error`. Defaults to `warning`. |
| timeout | integer | Timeout in milliseconds |
| projectPath | string | The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls. <br/>In the case you know only the current working directory you can use it as the project path.<br/>If you're not aware about the project path you can ask user about it. |

## Output
| Name | Type | Description |
| --- | --- | --- |
| items | array[object] |  |
| &nbsp;&nbsp;[].filePath* | string |  |
| &nbsp;&nbsp;[].notAnalyzedReason | string? |  |
| &nbsp;&nbsp;[].problems | array[object] |  |
| &nbsp;&nbsp;&nbsp;&nbsp;[].severity* | string |  |
| &nbsp;&nbsp;&nbsp;&nbsp;[].inspectionId | string? |  |
| &nbsp;&nbsp;&nbsp;&nbsp;[].description* | string |  |
| &nbsp;&nbsp;&nbsp;&nbsp;[].lineText* | string |  |
| &nbsp;&nbsp;&nbsp;&nbsp;[].line* | integer |  |
| &nbsp;&nbsp;&nbsp;&nbsp;[].column* | integer |  |
| &nbsp;&nbsp;[].timedOut | boolean? | Indicates whether the operation was timed out. 'true' value may mean that the results may be incomplete or partial. 'false', 'null' or missing value means that the operation has not been timed out. |
| more | boolean |  |

