# The local file with the base name $Name that the build event file $Events names, as a Windows path. The build
# names its top-level outputs in `namedSetOfFiles` records, one JSON record per line, and
# `--nobuild_event_json_file_path_conversion` makes every URI `file:///<drive>:/<path>`. An event file that names no
# such file answers an empty line. `polyglot_built_file` of polyglot-wrapper.sh is the same reader for the bash halves,
# and the `build` step of polyglot-wrapper.cmd is the one caller of this file.
#
# A regular expression and not a JSON parser: an event file of a large build has many records, and the JSON reader
# of PowerShell 5.1 would load each whole record. The URI is the one fact this reader needs.
#
# Cmdlets and the `[regex]` and `[System.Uri]` types only: Constrained Language Mode allows those and refuses a
# static call on `System.IO.File` or `System.IO.Path`. `Get-Content` in a pipeline streams the lines.
param(
    [Parameter(Mandatory = $true)][string]$Events,
    [Parameter(Mandatory = $true)][string]$Name
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

$found = ''
$pattern = '"uri":"(file://[^"]*)"'
Get-Content -LiteralPath $Events | ForEach-Object {
    foreach ($match in [regex]::Matches($_, $pattern)) {
        $path = ([System.Uri]$match.Groups[1].Value).LocalPath
        if ((Split-Path -Leaf $path) -ieq $Name) {
            $found = $path
        }
    }
}
Write-Output $found
