Message="Start thinking about cleaning out some stuff.  Theres a partition that is space  full."


echo 1


file="foo"

case $space in
[1-6]*)
  Message="All is quiet."
  ;;
[7-8]*)
  Message="Start thinking about cleaning out some stuff.  Theres a partition that is $space % full."
  ;;
9[1-8])
  Message="Better hurry with that new disk...  One partition is $space % full."
  ;;
99)
  Message="I'm drowning here!  There's a partition at $space %!"
  ;;
*)
  Message="I seem to be running with an nonexistent amount of disk space..."
  ;;
esac

case "$TARGET_CARD" in
    "" | all) TARGET_CARD=all;
    log_action_begin_msg "Setting up ALSA" ;;
esac

case $-:$BASH_VERSION in
*x*:[0123456789]*)	: bash set -x is broken :; set +ex ;;
esac

case :${lib_path1}:        in
  *:${lib}:*) ;;
  *) lib_path1=${lib_path1}:${lib} ;;
esac

case x`uname -r` in
x1.*)
	WHICH_LINUX=linux-c
	;;
esac

case x$WHICH_LINUX in #(vi
xauto)
	system=`uname -s 2>/dev/null`
	;;
esac

case `(typeset -u s=a n=0; ((n=n+1)); print $s$n) 2>/dev/null` in
A1)	shell=ksh
	typeset -u ID
	typeset -i counter err_line
	;;
*)	shell=bsh
	;;
esac

monthnoToName()
{
  # sets the variable 'month' to the appropriate value
  case $1 in
    1 ) month="Jan"    ;;  2 ) month="Feb"    ;;
    3 ) month="Mar"    ;;  4 ) month="Apr"    ;;
    5 ) month="May"    ;;  6 ) month="Jun"    ;;
    7 ) month="Jul"    ;;  8 ) month="Aug"    ;;
    9 ) month="Sep"    ;;  10) month="Oct"    ;;
    11) month="Nov"    ;;  12) month="Dec"    ;;
    * ) echo "$0: Unknown numeric month value $1" >&2; exit 1
  esac
   return 0
}

case "$3" in
	-)
	  #	output goes to standard output
    ;;
	*)
		#	output goes to a file
	  ;;
esac

case "$PACKER_BUILDER_TYPE" in
*)
    echo "Unknown Packer Builder Type >>$PACKER_BUILDER_TYPE<< selected."
 ;;  # <- One more issue
esac

case "$@" in
"-d "*)	echo DEBUGGING 1>&2
	debug=-d
	shift

esac


echo *""*


function tokenReader {
    reportDebugFuncEntry "$*" "config entry"

    [ "$3" ] || { reportError "Not enough arguments passed" ; return 1 ; }

    typeset function="$1"
    typeset name="$2"
    typeset keys="$3"

    typeset enttype
    typeset key
    typeset -a values
    typeset -i indx
    typeset varname
    typeset position
    typeset value
    typeset output

    # Reuse a previously set entry if it is about the same entry.
    case "$entry" in
      *" ${name}("*)
        reportDebug "Using previously set entry for $name"
        ;;
      *)
        reportDebug "Reading entry for $name"
        entry="$(entryReader "$name" "$config")"
        ;;
    esac

    # Check if the neccessary parameters are set.
    [ "$function" ] || { reportDebug "Function not specified"    ; return 1 ; }
    [ "$name" ]     || { reportDebug "Name not passed"           ; return 1 ; }
    [ "$keys" ]     || { reportDebug "Keys not passed for $name" ; return 1 ; }
    [ "$entry" ]    || { reportDebug "Entry not set for $name"   ; return 1 ; }

    # Read type from entry
    enttype="${entry%%[[:space:]]*}"
    members="$enttype,$name,$(cutParentheses "$entry")"

    let indx=0
    typeset IFS=","
    for key in $keys ; do
        # Try to read the positional index number from defined syntax.
        varname="syntax_${enttype}_${key}"

        reportDebug "Looking up $key for $name at position $position"
        if [ "$key" != "members" ]; then
            # Not looking for group members, use position.
            value="$position"
        else
            # Looking for members, which is a csv list in itself.
            value="$(cutParentheses "$entry")"
        fi

        (( "$position" )) || { reportError "Position $position for $varname unset" ; return 1 ; }

        if [ "$function" = "printVals" ]; then
            values[indx]="$value"
#            let indx+=1
        elif [ "$function" = "setVars" ]; then
            export "$key"="$value"
        else
            reportError "Invalid function passed: $function"
            return 1
        fi
    done

    typeset IFS=" "
    if [ "$function" = "printVals" ]; then
        output="${values[*]}"
        reportDebug "Output: $output"
        printf "$output\n"
    fi

    return 0
}