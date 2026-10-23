# yonder-client

The client of yonder, for `yonder` and `bt --host`. `Client` holds the configuration directory, the seam of the local
git and ssh calls (`Processes`), the sink of the stderr lines, and the Ctrl+C question. Each operation opens a new
connection per request: `run`, `attach`, `cancel`, `runs`, `lease`, `release`, `hello` and `prepare`.

## The files

| File | Content |
|---|---|
| `~/.config/yonder/identity.crt`, `identity.key` | The certificate and the key of the client, created on the first call. |
| `~/.config/yonder/hosts/<alias>.json` | `{"alias", "fingerprint", "port", "hostname", "addresses", "repo"}`. `prepare` writes it. A connection puts the address that answered first, and a cached address that failed last. An address of the host name replaces the cached ones, which all failed. |
| `~/.config/yonder/bases/<alias>/<holder>` | The commit of `refs/yonder/base/<holder>` on the host, after the last base sync. The next run adds it to the candidates of `Have`. |

## The refusals

Each refusal leaves `yonder` by 255. A refusal of the agent keeps the code of the agent: the `API.md` of `yonder-link`
lists those codes. The client adds these:

| Code | Input |
|---|---|
| `usage` | An alias that is not `[0-9A-Za-z._-]+`, a run without a command, or `prepare` without `--agent` outside a checkout of the monorepo. The last message names `--agent`. |
| `config` | No home directory, an identity that cannot be read or created, or a file that cannot be written. |
| `no_host` | No host file for the alias. The message names `bt --host <alias> --prepare` and `yonder prepare`. |
| `bad_host_file` | A host file that is not JSON of the host file shape, or that names another alias. |
| `unreachable` | No cached address and no address of the host name answers. The message names each address and the lookup. |
| `untrusted` | The agent shows a certificate that the host file does not pin, or it refuses the certificate of the client. |
| `schema` | The agent speaks another schema version. |
| `timed_out` | No reply within the deadline of the request. For `Follow`: no reply for 10 min, over all reconnects. |
| `link` | The connection closed, or a reply came that the protocol does not allow there, such as `Phases` outside a `Follow`. |
| `local_changes` | The checkout has tracked or untracked changes. |
| `behind` | The host has none of the candidate commits: HEAD, its first-parent ancestors at the distances 1, 2, 4 … 65536, and the merge bases with `master` and `origin/master`. The first `Have` names the distances up to 16, and only a host that has none of them gets the rest in a second `Have`. The message gives the commit count of the history and names `bt --host <alias> --prepare`. |
| `fetch_failed` | Fetch did not copy every staged file. The run stays on the host for `yonder fetch` and `yonder forget`. |
| `git_failed` | A local git call failed or ran past its deadline. |
| `ssh_failed` | An ssh call of `prepare` failed or ran past its deadline of 30 s (120 s for the copy). |
| `agent_failed` | The agent binary cannot be read, its Bazel build failed or wrote nothing for 30 min, `cquery` named no `yonderd` file, `yonderd setup` failed on the host, or it printed no setup line. |
| `interrupted` | Ctrl+C came before the run started, or a second Ctrl+C stopped the follow. A press after the start cancels the run, and the run then ends with the code of the host. |

A fetched path that is absolute, has `..` or `\`, is not below a `fetch` directory, or is inside a `.git` directory in
any case (`.GIT`, `.git.`, `git~1`) is skipped, and the fetch then fails. `yonder fetch` without `--fetch` takes the
`fetch` directories of the run from `Runs`. An agent before that field names none, and the fetch then refuses with
`usage`. Each file goes to `<name>.yonder-part` first and takes its name only at the size that the agent named. The run
removes itself on the host (`Forget`) only when the client saw its end and every file came.

A pack goes to a temporary file first, under `git pack-objects --progress` with a quiet limit of 60 s and no limit on
the whole pack. Each write to the agent has a deadline of 120 s.

## The agent of `prepare`

`yonder prepare <alias>` without `--agent` builds the agent in the root of the checkout of the working directory. An
ultimate root, with `community/tools/yonder`, builds `@community//tools/yonder:yonderd`. A community root builds
`//tools/yonder:yonderd`. The build is `./bazel.cmd build <label>` under a quiet limit of 30 min, as in
`bt --host --prepare`. Then `./bazel.cmd cquery --output=files <label>` names the binary relative to the execution
root, and `./bazel.cmd info execution_root` names that root. The monorepo has no `bazel-out` link in the checkout.

## The order of a run

1. `git rev-parse HEAD`, then `git status` and `Have` at the same time. Local changes refuse at once, and a host that
   has none of the candidates refuses with `behind`. Ctrl+C up to here runs nothing on the host.
2. `Start`. The host takes the lock and the lease at once. A start whose reply does not come is cancelled, and the
   run refuses with `link` or `timed_out`.
3. `Follow` on a thread of its own, up to `Phases` and `Exit`. The lines of the host show while the pack builds.
4. `Pack` on a connection of its own: the client opens it, builds the pack file meanwhile with an empty chunk every
   15 s, then sends the file. When the pack fails, the client cancels the run, stops the follow, and refuses with the
   code of the pack: `link`, `timed_out`, `git_failed`, or the code of the agent. The message says whether the cancel
   came.
5. A thread syncs the base: a thin pack of the local `origin/master`, or of `master`, to `refs/yonder/base/<holder>`.
   It goes once per run, after the pack of step 4. It skips the pack when the host has the commit. A failure is one
   `yonder: warning:` line on the stderr, and the run goes on. The end of the run waits for the sync, and Ctrl+C
   closes its connection.
6. `Fetch` and `Forget`.

Ctrl+C after the start, the build of the pack included, cancels the run on the host: the first press sends `Cancel`
and stops the pack, and the output follows up to the end of the run. The second press stops the follow at once.

## The steps

`RunOutcome.phases` lists the steps of a run with their durations: `pack` and `upload` of the client, the steps of the
host from `Reply::Phases`, `fetch`, and `total`, the whole call. `phases_line` makes the stderr line `yonder: phases:
pack 3.1s, upload 2.0s, lease 9m02s, … total 10m13s`. `yonder run` and `bt --host` print it at the end of a run. A run
that the agent refuses has no outcome, so the client says that line itself. `yonder runs` shows the steps of 0.1 s or
more of each run.
