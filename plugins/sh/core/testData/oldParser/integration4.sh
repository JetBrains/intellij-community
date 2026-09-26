case "$CONCURRENCY" in
  shell)
    startup() {
        [ 1 = "$backgrounded" ]
    }
    ;;
esac