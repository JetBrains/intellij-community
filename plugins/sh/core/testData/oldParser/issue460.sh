{ git log | cat; } |& perl -pe 's/^/\t$1/g';

declare -f "cmd_${ACTION}" >& /dev/null || {
	show_help "$ACTION is not a valid command";
	exit 1;
};