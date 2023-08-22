foo() { foo="$1"; shift; while [ $# -gt 0 ]; do case "$1" in ($foo) ;; (*) return 1;; esac; shift; done; }
