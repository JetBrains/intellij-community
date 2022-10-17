case "$script" in
  *.sh)
    ;;
  *)
    $debug "$script" $action &
    startup_progress
    backgrounded=1
    ;;
esac