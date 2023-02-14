dump-host-id() {
  {
    echo "Dummy text"
  } > $out_dir/kernel.txt
}

$1 > >(sed 's/^/OUTPUT  : /') 2> >(sed 's/^/OUTPUT  : /' >&2)