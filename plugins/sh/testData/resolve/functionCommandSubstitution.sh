lines_in_file () {
  cat "one.txt" | wc -l
}

num_lines=$(<caret>lines_in_file)