#!/bin/bash

branches="$(git for-each-ref --format='%(refname:short)' refs/remotes/)"
# some comment
echo 'Error'