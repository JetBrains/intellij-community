#!/bin/sh

if [[ ("$conf_branch" = r/*/*) || (("$conf_branch" != r/*) && ("$conf_branch" = */*)) ]]; then
  echo "Test output"
fi