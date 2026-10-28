# yonder-client

The client of yonder, for `yonder` and `bt --host`. `Client` holds the configuration directory, the seam of the local
calls (`Processes`), the sink of the stderr lines, the sink of the stdout lines of a run
(`Client::with_stdout`; without it they come in `RunOutcome::stdout`), and the Ctrl+C question. Each operation opens a
new connection per request: `run`, `run_with`, `attach`, `cancel`, `runs`, `hello`, `prepare` and `upgrade`.

## Branch Deck

The client starts no git. Branch Deck does every git call, through `bd -C <checkout> host …` (`bd` on the `PATH`):

| Call | What the client takes from it |
|---|---|
| `snapshot [--no-status \| --working-tree] [--blob .yonder.toml] --farthest <n>` | The commit, HEAD, the status lines, the repository name, the patch directory, the base and the candidates up to the distance `n`, and `.yonder.toml` of the commit. |
| `pack <commit> --have <id>…` | The thin pack in a private file of bd, its size and its commit count. The client streams the file and removes it. |
| `apply <patch>` | The count of the files that the patch changed in the working tree. |

Each call answers one JSON object. A failure answers the error object of bd and refuses with `bd_failed`, which names
the code and the message of bd. A missing bd, or one without `bd host`, refuses with `bd_failed` too, and the message
says what yonder needs. A failed `apply` refuses with `apply_failed`. A snapshot or an apply has a deadline of 300 s. A
pack has a quiet limit of 90 s on the progress lines of bd and no limit on the whole pack.

## The options of a run

`Client::run` is the run of `bt --host`. `Client::run_with` takes `RunOptions`, and the `yonder run` CLI sets each one:

| Option | `bt --host` | `yonder run` | Effect |
|---|---|---|---|
| `working_tree` | off | on, off with `--head` | The run ships the working tree: HEAD plus the tracked and untracked changes that git does not ignore, as one commit. Off, a checkout with local changes is refused with `local_changes`. |
| `nice` | none | `--nice`, 10 by default | The niceness of the prepare steps and the command on the host. So the runs of `bt --host` go first. |
| `outputs` | off | on | The outputs that `.yonder.toml` names come back, each in place of its local copy. |
| `apply` | off | `--apply` | The changes that the command made to the files come back as a patch, and `bd host apply` applies it to the working tree. |
| `repo_by_name` | off | on without `--repo` | The run goes to the repository of the same name as the local one beside the repository of the host file (`~/projects/branch-deck` beside `~/projects/idea-1`), or to the one of the host file when the host lacks it. |

### The commit of the working tree

`bd host snapshot --working-tree` writes it: HEAD when nothing changed, else a commit on HEAD. Its author and committer
are `yonder <yonder@yonder.invalid>` at the date of the committer of HEAD, and its message is `yonder: the working tree
on <HEAD>`. So the same tree on the same HEAD is the same commit, and a second run of it sends no pack. The index, the
files and the refs of the checkout stay as they are, and no ref names the commit. Ignored files stay local, and the
host has its own. bd reads the status in the untracked mode that the repository configures.

### The run record

Before `Start`, the client writes `<run>.json` in the patch directory of the snapshot, `yonder/` of the git directory
of the checkout: `{"commit", "fetch", "outputs", "changes"}`. `fetch` holds the `fetch` directories of the run.
`outputs` holds the outputs of `.yonder.toml` of the shipped commit when the run has `outputs`, else none. The snapshot
carries the file, and the client parses it with `config` of `yonder-link`, the parser of the agent. A commit without
the file has no outputs. A file that does not parse refuses with `bad_config` before the start. `changes` is `apply`. A record and a patch older than 7 days there go.

The record says what a fetch takes. So the host cannot choose which local paths it replaces: see [The fetch](#the-fetch).

### The changes

With `apply`, the patch goes to `yonder/<run>.patch` in the git directory of the checkout, beside the record. `bd host apply` applies it to the files of the
working tree, not to the index, all or nothing. A patch that does not apply, such as after a local edit of the same lines, refuses with
`apply_failed` and names the patch, and the working tree stays as it was. `yonder fetch` of such a run writes the patch
there too.

## The files

| File | Content |
|---|---|
| `~/.config/yonder/identity.crt`, `identity.key` | The certificate and the key of the client, created on the first call. |
| `~/.config/yonder/hosts/<alias>.json` | `{"alias", "fingerprint", "port", "hostname", "addresses", "repo"}`. `prepare` writes it. A connection puts the address that answered first, and a cached address that failed last. An address of the host name replaces the cached ones, which all failed. |
| `~/.config/yonder/bases/<alias>/<holder>` | The commit of `refs/yonder/base/<holder>` on the host, after the last base sync. The next run adds it to the candidates of `Have`. |

## The refusals

A refusal carries an `Exit`, which is the exit code of `yonder`:

| Exit | Code | When |
|---|---|---|
| `NotRun` | 75 | A run refused before its command: the host did not run it, so the caller can run it locally instead. Every refusal before the start, a pack that did not reach the host, and a refusal of the agent whose phases have no `command`, such as `lease_failed`, `prepare_failed`, `old_git` or `bad_config`, but `unknown_run`. |
| `Interrupted` | 130 | Ctrl+C before the command, or a second Ctrl+C during the follow. |
| `Refused` | 255 | Any other refusal: the link broke after the start and the run can go on on the host, the run vanished on the host after its start (`unknown_run`: a `yonder forget` or the expiry removed it, so whether its command ran is unknown, and the client forgets nothing), the changes did not apply, or a request other than a run failed. |

A refusal of the agent keeps the code of the agent: the `API.md` of `yonder-link` lists those codes. The client adds
these:

| Code | Input |
|---|---|
| `usage` | An alias that is not `[0-9A-Za-z._-]+`, a run without a command, a working directory of `yonder` in no git checkout, a `fetch` of a run id that cannot name a file, a `fetch` without `--fetch` of a run that has no record in this checkout, a record that is not JSON of the record shape, or `prepare` without `--agent` outside a checkout of the monorepo. The last message names `--agent`. |
| `config` | No home directory, an identity that cannot be read or created, or a file that cannot be written, such as the run record. |
| `no_host` | No host file for the alias. The message names `bt --host <alias> --prepare` and `yonder prepare`. |
| `bad_host_file` | A host file that is not JSON of the host file shape, or that names another alias. |
| `unreachable` | No cached address and no address of the host name answers. The message names each address and the lookup. On macOS it also names Local Network privacy when an address of the local network failed with `EHOSTUNREACH` and the relay through `/usr/bin/nc` cannot pass the refusal. |
| `untrusted` | The agent shows a certificate that the host file does not pin, or it refuses the certificate of the client. |
| `schema` | The agent speaks another schema version. |
| `timed_out` | No reply within the deadline of the request. For `Follow`: no reply for 10 min, over all reconnects. |
| `link` | The connection closed, or a reply came that the protocol does not allow there, such as `Phases` outside a `Follow`. |
| `local_changes` | The checkout has tracked or untracked changes. |
| `behind` | The host has none of the candidate commits: HEAD, its first-parent ancestors at the distances 1, 2, 4 … 65536, and the merge bases with `master` and `origin/master`. The first `Have` names the distances up to 16, and only a host that has none of them gets the rest in a second `Have`. The message gives the commit count of the history and names `bt --host <alias> --prepare`. |
| `fetch_failed` | Fetch did not copy every staged file. The run stays on the host for `yonder fetch` and `yonder forget`. |
| `bd_failed` | A call of `bd host` failed or ran past its limit: bd is missing or has no `bd host`, or its git failed. The message names the code and the message of bd. |
| `bad_config` | `.yonder.toml` of the shipped commit of a run with `outputs` does not parse, or it is not a UTF-8 file. The message is the one of the agent, with the commit. |
| `ssh_failed` | An ssh call of `prepare` failed or ran past its deadline of 30 s (120 s for the copy). |
| `agent_failed` | The agent binary cannot be read, its Bazel build failed or wrote nothing for 30 min, `cquery` named no `yonderd` file, `yonderd setup` failed on the host, or it printed no setup line. For `upgrade`: a host that is not macOS, a binary of another platform than the hello names, a bootstrap run that failed or printed no line of `yonderd upgrade`, or an agent that answers with a third binary at the end. |
| `rolled_back` | The new agent of `upgrade` did not answer in time, and the previous binary answers: the watchdog brought it back. The message names `~/.cache/yonderd/upgrade.json` and the log of the agent. |
| `interrupted` | Ctrl+C came before the run started, or a second Ctrl+C stopped the follow. A press after the start cancels the run, and the run then ends with the code of the host. |
| `old_agent` | An upgrade of an agent without the feature `upgrade`. The message names `yonder prepare`. |
| `apply_failed` | The changes of the command do not apply to the working tree, or they did not come because the fetch failed. The message gives the exit code of the command and the patch file, or `yonder fetch`, which writes the patch into the git directory of the checkout. |

### The fetch

A fetch takes only what the run record asks for. A fetched path that is absolute, has `..` or `\`, is not below a
`fetch` directory of the record or its output, or is inside a `.git` directory in any case (`.GIT`, `.git.`, `git~1`) is
skipped, and the fetch then fails. A file of an output that the record does not name, and the changes of a record
without `changes`, are skipped too. The first file of an output removes the local file or directory of the output.
`yonder fetch` reads the record of the run in the checkout of the working directory. An explicit `--fetch` replaces
the `fetch` directories of the record. Without a record, `--fetch` takes the staged files below its directories only,
and no `--fetch` refuses with `usage`. No write follows a local link. A file whose target has a link in a parent directory below the checkout is skipped,
and so is an output whose local copy has one: the removal of the copy would leave the checkout. The part is a new file
that the open makes itself (`create_new`, and `O_NOFOLLOW` on Unix), after the removal of a part that a cut fetch
left, whether a file or a link. A stream writes at most the size that the agent named, and the client reads and drops
the rest. Each file goes to `<name>.yonder-part` first and takes its name only at the size that the agent named. The run
removes itself on the host (`Forget`) only when the client saw its end and every file came.

A pack goes to a private file of bd first, with a quiet limit of 90 s on the progress of bd and no limit on the whole
pack. Each write to the agent has a deadline of 120 s.

## The agent of `prepare`

`yonder prepare <alias>` without `--agent` builds the agent in the root of the checkout of the working directory. An
ultimate root, with `community/tools/yonder`, builds `@community//tools/yonder:yonderd`. A community root builds
`//tools/yonder:yonderd`. The build is `./bazel.cmd build <label>` under a quiet limit of 30 min, as in
`bt --host --prepare`. Then `./bazel.cmd cquery --output=files <label>` names the binary relative to the execution
root, and `./bazel.cmd info execution_root` names that root. The monorepo has no `bazel-out` link in the checkout.

## The upgrade

`Client::upgrade` updates the agent of a macOS host over the link:

1. The hello names the platform, the features and the SHA-256 of the binary of the agent. A host that is not macOS
   refuses with `agent_failed` and names `yonder prepare`.
2. The local binary is `--agent`, or the build of `@community//tools/yonder:yonderd` as for `prepare`. A binary of
   another platform than the hello refuses before it goes: the client reads the Mach-O or ELF header. The binary that
   the agent runs already is current, and nothing goes, unless `--force`.
3. An agent with the feature `upgrade` gets `Request::Upgrade` with the binary, and `force` with `--force`.
4. An agent without the feature refuses with `old_agent` before the binary goes: `yonder prepare` installs a new
   agent over ssh.
5. The client connects again, every 1 s to 5 s, until the hello names the new binary, up to 110 s: the drain of the old
   agent (20 s), the wait of the watchdog (60 s) and 30 s for launchd. The previous binary at the end refuses with
   `rolled_back`, no answer with `unreachable`.

## The order of a run

1. The commit: `bd host snapshot --no-status` with the candidates up to the distance 16, then a snapshot with the status
   and `Have` at the same time; local changes refuse at once. With `working_tree`, the snapshot of the working tree
   first, then `Have`. A host that has none of the near candidates gets a second `Have` with the candidates of a
   snapshot up to the distance 65536. A host that has none of the candidates refuses
   with `behind`. The hello of the agent names its features, so a run that needs one that the agent lacks refuses here.
   Then the client writes the run record. Ctrl+C up to here runs nothing on the host.
2. `Start`. The host lends the run a host slot, or puts it in the queue of the slots. A start whose reply does not come is cancelled, and the
   run refuses with `link` or `timed_out`.
3. `Follow` on a thread of its own, up to `Phases` and `Exit`. The lines of the host show while the pack builds.
4. `Pack` on a connection of its own: the client opens it, builds the pack file meanwhile with an empty chunk every
   15 s, then sends the file. When the pack fails, the client cancels the run, stops the follow, and refuses with the
   code of the pack: `link`, `timed_out`, `bd_failed`, or the code of the agent. The message says whether the cancel
   came.
5. A thread syncs the base: a thin pack of the local `origin/master`, or of `master`, to `refs/yonder/base/<holder>`.
   It goes once per run, after the pack of step 4. It skips the pack when the host has the commit. A failure is one
   `yonder: warning:` line on the stderr, and the run goes on. The end of the run waits for the sync, and Ctrl+C
   closes its connection.
6. `Fetch` and `Forget`. A run whose record names outputs or changes fetches even without a `fetch` directory.
7. With `apply`, `bd host apply` of the patch.

Ctrl+C after the start, the build of the pack included, cancels the run on the host: the first press sends `Cancel`
and stops the pack, and the output follows up to the end of the run. The second press stops the follow at once.

## The steps

`RunOutcome.phases` lists the steps of a run with their durations: `pack` and `upload` of the client, the steps of the
host from `Reply::Phases`, `fetch`, and `total`, the whole call. The line of a started run is `yonder: run <id> of
<commit> on <alias>`, with ` in <repository>` when the repository is not the one of the host file, and `: the working
tree, HEAD <commit> and <n> changed paths` for a commit of the working tree. `phases_line` makes the stderr line `yonder: phases:
pack 3.1s, upload 2.0s, slot 9m02s, … total 10m13s`. `yonder run` and `bt --host` print it at the end of a run. A run
that the agent refuses has no outcome, so the client says that line itself. `yonder runs` shows the steps of 0.1 s or
more of each run.
