# pythonproject

PY-91629: a uv workspace where `sub-project-a` references the sibling member `sub-project-b`
only through PEP 621 `[project.optional-dependencies]` (an extra), never through
`[project].dependencies`. `[tool.uv.sources]` marks it as a workspace source, so the member
must still become a module dependency — otherwise `import sub_project_b` is reported as an
unresolved import.
