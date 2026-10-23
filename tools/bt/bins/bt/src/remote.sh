# The host half of `bt --host`. `remote.rs` runs it over ssh as
# `sh -c <this text> bt-remote <mode> <repo> <holder> <id> <commit> <idle hours> <bt args>...`.
#
# Mode `run` keeps one warm lease of the main repository <repo> for each <holder>, which is one local checkout. The
# lease id and the slot stay in `~/.cache/bt-remote/leases/<holder>.lease`, so a later run of the same holder checks
# out its commit in the same slot, beside the same Bazel server. A lock directory beside that file makes a second run
# of the same holder wait. The script checks out <commit> in the slot, runs bt with the bt args, and moves the new run
# directories of `out/bt-runs` to the stage `~/.cache/bt-remote/runs/<id>`. On every way out it deletes the ref
# `refs/bt-remote/<id>` that the push made. The stdout of bt is the stdout of this script, and the exit code of bt is
# its exit code. 6, the infrastructure code of bt, is a failure before bt ran.
#
# Before the run, the script returns the lease of each other holder that no run used for <idle hours>. It first
# checks out the HEAD of <repo> in that slot, so the return gives up no commit.
#
# Mode `lease` takes or reuses the lease of <holder> and runs nothing, for `--prepare --take-lease`.
#
# Mode `release` returns the lease of <holder>. It passes `--confirm-preserved`, because the commit in the slot came
# from the local checkout of the holder, which keeps it.
#
# The end of the stdin tells that the caller is gone: Ctrl+C, a kill or a lost connection. Then the script interrupts
# the bt process group, waits for it, removes the stage, and cleans up as on any other way out.
set -u
mode=$1 repo=$2 holder=$3 id=$4 commit=$5 idle_hours=$6
shift 6
case $repo in "~/"*) repo=$HOME/${repo#"~/"} ;; esac
leases=$HOME/.cache/bt-remote/leases
stage=$HOME/.cache/bt-remote/runs/$id
ref=refs/bt-remote/$id
owned=
slot=
lease_id=
bt=
before=
code=6

say() {
  printf 'bt --host: %s\n' "$*" >&2
}

# The value of a key of a lease file.
field() {
  sed -n "s/^$1=//p" "$2" 2>/dev/null
}

# The value of a string key of the one-line JSON of bd.
json_field() {
  printf '%s\n' "$2" | sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p"
}

# Takes the lock of a holder. A lock whose process is gone is taken over.
lock() {
  if mkdir "$leases/$1.lock" 2>/dev/null; then
    echo $$ >"$leases/$1.lock/pid"
    return 0
  fi
  lock_pid=$(cat "$leases/$1.lock/pid" 2>/dev/null)
  if [ -n "$lock_pid" ] && ! kill -0 "$lock_pid" 2>/dev/null; then
    rm -rf "$leases/$1.lock"
    if mkdir "$leases/$1.lock" 2>/dev/null; then
      echo $$ >"$leases/$1.lock/pid"
      return 0
    fi
  fi
  return 1
}

unlock() {
  rm -rf "$leases/$1.lock"
}

# Whether bd still knows the lease.
lease_alive() {
  case $(bd lease status -C "$repo" </dev/null 2>/dev/null) in
    *"\"lease_id\":\"$1\""*) return 0 ;;
  esac
  return 1
}

# Returns the lease of an idle holder, after its slot went back to the HEAD of the main repository.
give_back() {
  idle_slot=$(field slot "$1")
  idle_lease=$(field lease_id "$1")
  if ! lease_alive "$idle_lease"; then
    rm -f "$1"
    return
  fi
  head=$(git -C "$repo" rev-parse HEAD) || return
  if ! git -C "$idle_slot" checkout -q -f --detach "$head"; then
    say "cannot check out $head in the idle slot $idle_slot"
    return
  fi
  if returned=$(cd / && bd -C "$idle_slot" lease return --lease-id "$idle_lease" </dev/null 2>&1); then
    rm -f "$1"
    say "returned the idle lease $idle_lease of $idle_slot"
  else
    say "cannot return the idle lease $idle_lease of $idle_slot: $returned"
  fi
}

# Returns the leases of the other holders that no run used for the idle hours. Its variables have names of their own,
# because a shell function shares the variables of the script.
sweep() {
  sweep_now=$(date +%s)
  for sweep_file in "$leases"/*.lease; do
    [ -e "$sweep_file" ] || continue
    sweep_holder=$(basename "$sweep_file" .lease)
    [ "$sweep_holder" = "$holder" ] && continue
    sweep_used=$(field used "$sweep_file")
    [ $((sweep_now - ${sweep_used:-0})) -ge $((idle_hours * 3600)) ] || continue
    lock "$sweep_holder" || continue
    give_back "$sweep_file"
    unlock "$sweep_holder"
  done
}

finish() {
  # The caller can be gone, and a write to its closed stderr must not stop the cleanup.
  trap '' PIPE INT TERM HUP
  if [ -n "$bt" ]; then
    # An interrupted bazel client cancels its build before it exits.
    waited=0
    while kill -0 -- "-$bt" 2>/dev/null && [ "$waited" -lt 120 ]; do
      sleep 1
      waited=$((waited + 1))
    done
  fi
  if [ -e "$stage/.gone" ]; then
    rm -rf "$stage"
  elif [ -n "$bt" ]; then
    for run in $(ls "$slot/out/bt-runs" 2>/dev/null); do
      case " $before " in
        *" $run "*) ;;
        *) mv "$slot/out/bt-runs/$run" "$stage/" ;;
      esac
    done
  fi
  rmdir "$stage" 2>/dev/null
  if [ -n "$lease_id" ]; then
    printf 'lease_id=%s\nslot=%s\nused=%s\n' "$lease_id" "$slot" "$(date +%s)" >"$leases/$holder.lease.tmp" &&
      mv -f "$leases/$holder.lease.tmp" "$leases/$holder.lease"
  fi
  [ -n "$owned" ] && unlock "$holder"
  if [ "$mode" = run ]; then
    git -C "$repo" update-ref -d "$ref" || say "cannot delete $ref in $repo"
  fi
  exit "$code"
}
trap finish EXIT
trap 'exit' INT TERM HUP

mkdir -p "$leases" "$stage" || exit
waited=0
until lock "$holder"; do
  [ "$waited" -eq 0 ] && say "waiting for the other run of $holder on this host"
  [ "$waited" -lt 3600 ] || {
    say "the other run of $holder holds its lease for an hour; giving up"
    exit
  }
  sleep 2
  waited=$((waited + 2))
done
owned=1

file=$leases/$holder.lease
if [ "$mode" = release ]; then
  if [ ! -e "$file" ]; then
    echo "$holder holds no lease on this host"
    code=0
    exit
  fi
  released_slot=$(field slot "$file")
  released_lease=$(field lease_id "$file")
  if lease_alive "$released_lease"; then
    returned=$(cd / && bd -C "$released_slot" lease return --lease-id "$released_lease" --confirm-preserved </dev/null 2>&1) || {
      say "cannot return the lease $released_lease of $released_slot: $returned"
      exit
    }
  fi
  rm -f "$file"
  echo "released the lease $released_lease of $released_slot"
  code=0
  exit
fi

sweep
file=$leases/$holder.lease
if [ -e "$file" ] && lease_alive "$(field lease_id "$file")"; then
  slot=$(field slot "$file")
  lease_id=$(field lease_id "$file")
  say "lease $slot (warm)"
else
  rm -f "$file"
  leased=$(bd lease get --holder "$holder" -C "$repo" </dev/null) || {
    say "bd lease get failed in $repo: $leased"
    exit
  }
  slot=$(json_field path "$leased")
  lease_id=$(json_field lease_id "$leased")
  if [ -z "$slot" ] || [ -z "$lease_id" ]; then
    say "bd lease get answered no lease: $leased"
    lease_id=
    exit
  fi
  say "lease $slot (new)"
fi
if [ "$mode" = lease ]; then
  echo "the lease $lease_id of $holder is $slot"
  code=0
  exit
fi
# The slot belongs to this holder, so a file that the last run left changed is discarded.
git -C "$slot" checkout -q -f --detach "$commit" || {
  say "cannot check out $commit in $slot"
  exit
}
before=$(ls "$slot/out/bt-runs" 2>/dev/null | tr '\n' ' ')

# Job control gives bt and the watch a process group each, and keeps the stdin of the watch.
set -m
(cd "$slot" && exec ./community/tools/bt.cmd "$@") </dev/null &
bt=$!
(
  cat >/dev/null
  : >"$stage/.gone"
  kill -s INT -- "-$bt"
) >/dev/null 2>&1 &
watch=$!
set +m
wait "$bt"
code=$?
kill -- "-$watch" 2>/dev/null
exit "$code"
