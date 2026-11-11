---
name: python-tools
description: PyCharm environment tools. MUST be consulted before running ANY Python toolchain command (python, python3, pip, pip3, uv, conda, pytest, ruff, mypy, python -m <module>, etc.) — call get_python_environment first to get the executable and package manager that match PyCharm's configured SDK for the file's module. Also covers configuring an interpreter when none is set up.
allowed-tools: execute_tool
---

# PyCharm Environment Tools

Call `execute_tool` from the `jetbrains_air_ide` MCP server when it is available. That server is bound to the current AIR session; a
same-named tool from another MCP server may target a different IDE instance.

> **Mandatory pre-flight:** before running ANY Python command — `python`, `python3`, `pip`, `pip3`, `uv`, `conda`, `pytest`, `ruff`, `mypy`, `python -m <module>`, or anything else that resolves a Python interpreter — call `get_python_environment` on a file inside the target module. Use the returned `executablePath` instead of a bare `python` / `python3`, and the returned `packageManager` to choose the correct install command. Skipping this step silently picks the wrong interpreter (system Python instead of the project's venv, an older minor version, missing packages) and corrupts the project's environment.

> **If `get_python_environment` returns a non-null `noInterpreterConfigured` field, get a proper interpreter before running the Python command.** Branch on the field's content:
> - Ends with `call configure_python_interpreter with the same --filePath.` → call `configure_python_interpreter`, then re-call `get_python_environment`. Run the Python command using the env info returned by the second call.
> - Says `PyCharm expects this project to use <tool>, but <tool> is not installed.` (e.g. `uv`, `poetry`, `hatch`) → do NOT call `configure_python_interpreter`. Ask the user to install the named tool first, then re-call `get_python_environment`.
> - Plain `No Python interpreter configured for: <path>.` (no extra suffix) → PyCharm has no automatic option, but the project also doesn't dictate a specific tool. Set up the env yourself with a sensible default (e.g. `python3 -m venv .venv` in the project root), then re-call `get_python_environment`. The new venv should be detected and the next response will suggest `configure_python_interpreter`; if it still returns plain "no interpreter" for some reason, use `.venv/bin/python` directly. Tell the user what you set up.
>
> For commands that aren't tied to a specific file (e.g. `pip install`, `pytest` at the project root, or a run configuration that doesn't expose a target file), pass any `.py` file inside the target module as `--filePath` — the interpreter is per-module, not per-file.

Two tools, used together:

1. **`get_python_environment`** — always call this first. Read-only: it reports the Python interpreter PyCharm has configured for a file (version, environment type, executable path, package manager) and never modifies project state.
2. **`configure_python_interpreter`** — call this *only* when `get_python_environment` returns a `noInterpreterConfigured` field whose value ends with `call configure_python_interpreter with the same --filePath.` That means PyCharm can do the configuration without installing anything new — it will attach an existing virtual environment on disk, inherit the parent module's interpreter, or create a fresh env using a tool that's already installed (uv / poetry / hatch / venv). Do not call it in any other situation.

Prefer these tools over guessing interpreter paths or package managers — the results match what PyCharm itself uses.

## `get_python_environment`

Returns the Python environment configured for the given file.

### Syntax

```
execute_tool(command="get_python_environment --filePath <value>")
```

### Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `--filePath` | `string` | Yes | Absolute or project-relative path to any Python file (e.g. `/Users/dev/myproject/src/main.py` or `src/main.py`). Used to identify which interpreter and environment PyCharm has configured for that file. |

**String parameters:** Quote values containing spaces: `--filePath '/Users/dev/my project/main.py'`

### Output fields

Response is a JSON object with these fields:

| Field | Description |
|-------|-------------|
| `pythonVersion` | Interpreter version string, e.g. `Python 3.11.4` |
| `environmentType` | `venv`, `conda`, `system`, or `unknown` |
| `executablePath` | Absolute path to the Python binary to invoke |
| `environmentPath` | Root of the virtual environment (venv prefix or conda prefix); `null` for system Python |
| `packageManager` | `pip`, `uv`, `poetry`, `hatch`, `conda`, or `unknown` — use this to pick the right install command |
| `noInterpreterConfigured` | When non-null, no interpreter is set up. Contains the next-step hint (see Pitfalls). Other fields will be `null`. |

Example response:

```json
{
  "pythonVersion": "Python 3.11.4",
  "environmentType": "venv",
  "executablePath": "/Users/dev/myproject/.venv/bin/python",
  "environmentPath": "/Users/dev/myproject/.venv",
  "packageManager": "uv"
}
```

### Examples

#### 1. Absolute path

```json
{"tool":"execute_tool","arguments":{"command":"get_python_environment --filePath /Users/dev/myproject/src/main.py"}}
```

#### 2. Project-relative path

```json
{"tool":"execute_tool","arguments":{"command":"get_python_environment --filePath src/main.py"}}
```

#### 3. Path with spaces

```json
{"tool":"execute_tool","arguments":{"command":"get_python_environment --filePath '/Users/dev/my project/src/main.py'"}}
```

### Pitfalls

- Quote the path if it contains spaces.
- The file must exist on disk; passing a non-existent path returns an error.
- If `noInterpreterConfigured` is non-null and ends with `call configure_python_interpreter with the same --filePath.` (e.g. "PyCharm can attach the existing virtual environment…", "PyCharm can create a new uv environment…", "PyCharm can inherit the interpreter from parent module 'X'…"), follow that — PyCharm has tooling ready and the configure tool will set it up without installing anything.
- If `noInterpreterConfigured` says `PyCharm expects this project to use <tool>, but <tool> is not installed.`, the project's `pyproject.toml` / lockfile points at a tool the system doesn't have. Do not call `configure_python_interpreter`. Ask the user before installing the named tool via the terminal, then re-call `get_python_environment`.
- If `noInterpreterConfigured` is the plain `No Python interpreter configured for: <path>.` with no extra content, do not call `configure_python_interpreter`. The project doesn't pin a specific tool, so set up a default venv yourself (e.g. `python3 -m venv .venv`) and re-call `get_python_environment` — the next call should detect it and suggest `configure_python_interpreter`. Never just run the user's command with bare `python` / `python3`.

## `configure_python_interpreter`

Asks PyCharm to attach a Python interpreter for the module the given file belongs to. PyCharm picks the right action: attach an existing virtual environment on disk, inherit the interpreter from a parent module, or create a fresh env using already-installed tooling (uv venv, poetry env, hatch env, or plain `venv`). Only call this when `get_python_environment` explicitly suggested it (the `noInterpreterConfigured` field ended with `call configure_python_interpreter with the same --filePath.`). Do not call it as a generic interpreter switcher and do not call it when `noInterpreterConfigured` is the plain "no interpreter" message without a configuration suggestion.

### Syntax

```
execute_tool(command="configure_python_interpreter --filePath <value>")
```

### Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `--filePath` | `string` | Yes | Absolute or project-relative path to a Python file inside the module whose interpreter should be configured. |

### Output

On success, returns the same JSON shape as `get_python_environment`, describing the interpreter PyCharm just configured.

### Examples

#### Configure after `get_python_environment` reported that PyCharm can set one up

```json
{"tool":"execute_tool","arguments":{"command":"configure_python_interpreter --filePath src/main.py"}}
```

### Pitfalls

- Local interpreters only. Remote / WSL / Docker interpreters are not supported by this tool.
- Only call this tool when `get_python_environment` told you to. Calling it after the plain `No Python interpreter configured for: <path>` (with no `call configure_python_interpreter` suggestion) will fail — PyCharm has no automatic option there and there is nothing this tool can do.

## Rules

1. **You MUST call `get_python_environment` before every Python toolchain invocation** — `python`, `python3`, `pip`, `pip3`, `uv`, `conda`, `pytest`, `ruff`, `mypy`, `python -m <module>`, and any other command that resolves a Python interpreter. The check is cheap (a single MCP call) and there is no exemption for "obvious" cases — running a fresh `python script.py` in a project with a configured venv is exactly the case where the wrong interpreter ruins things silently. Re-check after every `configure_python_interpreter` call, after the user reconfigures the SDK in PyCharm, and whenever you switch to a file from a different module.
2. **If `get_python_environment` returns a non-null `noInterpreterConfigured` field, get a proper interpreter before running the Python command — never fall back to bare `python` / `python3`.** Branch on the field's content:
   - Suffix `call configure_python_interpreter with the same --filePath.` → call `configure_python_interpreter`, re-call `get_python_environment`, then proceed.
   - `PyCharm expects this project to use <tool>, but <tool> is not installed.` → do NOT call `configure_python_interpreter`. Ask the user before installing the named tool via the terminal, then re-call `get_python_environment`.
   - Plain `No Python interpreter configured for: <path>.` → the project pins no specific tool. Set up a default venv yourself (`python3 -m venv .venv` in the project root) and re-call `get_python_environment`; the new venv will normally surface a `configure_python_interpreter` suggestion on the next call. Tell the user what you set up.
3. **If any other tool reports a missing Python interpreter, route through this skill before asking the user.** When `get_run_configurations`, `execute_run_configuration`, `build_project`, the terminal, or any other PyCharm tool returns a "no interpreter configured" message, call `get_python_environment` on a file in the target module — do NOT instruct the user to set the interpreter via Settings → Project → Python Interpreter without first checking what `get_python_environment` reports.
4. Use the returned `executablePath` verbatim — never substitute `python` / `python3` / `which python` output.
5. Use the returned `packageManager` to pick the install command — `pip install <pkg>` for `pip`, `uv add <pkg>` for `uv`, `poetry add <pkg>` for `poetry`, `hatch run pip install <pkg>` for `hatch`, `conda install <pkg>` for `conda`. If `packageManager` is `unknown`, ask the user how to install packages for this interpreter before running anything.
6. Call `configure_python_interpreter` only when `get_python_environment` explicitly suggested it (the `noInterpreterConfigured` field ended with `call configure_python_interpreter with the same --filePath.`). `get_python_environment` itself never modifies project state.
