package com.intellij.ide.starter.ide

/** Starts the line of the fluxbox log that tells that the IDE started without a window manager. */
internal const val NO_WINDOW_MANAGER_MESSAGE: String = "fluxbox is not the window manager"

/**
 * Runs the IDE from its arguments. Before the IDE starts, it starts fluxbox when `DISPLAY` has no window manager.
 * The first argument is the fluxbox log. The IDE replaces the shell, so the IDE keeps the process of the start.
 * fluxbox becomes a child of the IDE and stops with the X server.
 */
internal val windowManagerScript: String = ($$"""
  log="$1"
  shift
  display_seconds=30
  xprop_seconds=2
  wm_seconds=10
  wm_attempts=3
  """ +
  // a busy X server can block xprop, so each call gets a time limit
  $$"""
  limit=
  command -v timeout >/dev/null 2>&1 && limit="timeout $xprop_seconds"
  wm_check() { $limit xprop "$@" _NET_SUPPORTING_WM_CHECK 2>/dev/null | sed -n 's/.*window id # //p'; }
  """ +
  // EWMH: the window must name itself, so a property of an exited window manager does not count
  $$"""
  wm_running() { id=$(wm_check -root); [ -n "$id" ] && [ "$(wm_check -id "$id")" = "$id" ]; }
  """ +
  // the clock, not the number of checks, limits a wait, because one check can take two xprop calls
  $$"""
  seconds_since() { echo $(($(date +%s) - $1)); }
  """ +
  // xprop exits with 0 also when the property is missing, so the exit code tells only that the X server accepts a client
  $$"""
  x_reachable() { $limit xprop -root _NET_SUPPORTING_WM_CHECK >/dev/null 2>&1; }
  """ +
  // one line, so the run reports it with the message before it
  $$"""
  x_state() {
    echo "fluxbox exit code: $1; xprop: $($limit xprop -root _NET_SUPPORTING_WM_CHECK 2>&1 | tr '\n' ' ');" \
      "sockets: $(ls -l /tmp/.X11-unix 2>&1 | tr '\n' ' '); lock: $(tr -d ' \n' 2>/dev/null <"/tmp/.X$display-lock" || echo none);" \
      "X servers: $(ps -eo pid,ppid,etimes,args 2>/dev/null | sed -n '/[X]vfb\|[X]org/p' | tr '\n' ';')"
  }
  """ +
  // a DISPLAY without a colon, such as NoDisplay of a remote dev backend, names no X server
  $$"""
  if [ "${DISPLAY#*:}" != "$DISPLAY" ]; then
    start=$(date +%s)
    if [ "${DISPLAY#:}" != "$DISPLAY" ]; then
      display="${DISPLAY#:}"
      display="${display%%.*}"
      while [ ! -e "/tmp/.X11-unix/X$display" ] && [ $(seconds_since $start) -lt $display_seconds ]; do
        sleep 0.5
      done
    fi
  """ +
  // the socket can exist before the X server accepts a client
  $$"""
    if command -v xprop >/dev/null 2>&1; then
      until x_reachable || [ $(seconds_since $start) -ge $display_seconds ]; do
        sleep 0.5
      done
    fi
    if ! command -v xprop >/dev/null 2>&1 || ! command -v fluxbox >/dev/null 2>&1; then
      echo "xprop or fluxbox is not installed, so no window manager starts on $DISPLAY" >&2
    elif ! wm_running; then
  """ +
  // on a loaded agent fluxbox can register only after many seconds, so only an exited fluxbox starts again
  $$"""
      attempt=0
      while [ $attempt -lt $wm_attempts ]; do
        attempt=$((attempt + 1))
        fluxbox >>"$log" 2>&1 &
        wm=$!
        start=$(date +%s)
        registered=false
        while :; do
          if wm_running; then registered=true; break; fi
          [ $(seconds_since $start) -lt $wm_seconds ] && kill -0 $wm 2>/dev/null || break
          sleep 0.5
        done
  """ +
  // xprop can time out while a window manager runs, so the started fluxbox can lose to it
  $$"""
        if $registered; then
          echo "a window manager runs on $DISPLAY after about $(seconds_since $start) s" >&2
          break
        fi
        if kill -0 $wm 2>/dev/null; then
          tail -n 5 "$log" >&2
  """ +
  // the log gets the lines too, so the run reports them; the state shows what fluxbox waits for
  $$"""
          {
            echo "$$NO_WINDOW_MANAGER_MESSAGE on $DISPLAY after $wm_seconds s, see $log"
            echo "fluxbox state: $(sed -n 's/^State:[[:space:]]*//p' /proc/$wm/status 2>/dev/null), wchan: $(cat /proc/$wm/wchan 2>/dev/null)"
          } | tee -a "$log" >&2
          break
        fi
        wait $wm
        code=$?
        echo "fluxbox exited before it became the window manager on $DISPLAY, attempt $attempt/$wm_attempts, see $log" >&2
        if [ $attempt -eq $wm_attempts ]; then
          echo "$$NO_WINDOW_MANAGER_MESSAGE on $DISPLAY, because fluxbox exited $wm_attempts times, see $log" | tee -a "$log" >&2
        fi
        x_state $code >>"$log"
  """ +
  // a quick exit can repeat at once, so the next attempt waits a little
  $$"""
        [ $attempt -lt $wm_attempts ] && sleep 1
      done
    fi
  fi
  exec "$@"
""").trimIndent()
