${parameter:offset}
${parameter:offset:length}

${parameter:-word}
${parameter:=word}
${parameter:?word}
${parameter:+word}
${parameter}

${parameter@Q}
${parameter@E}
${parameter@P}
${parameter@A}
${parameter@a}
${parameter@@}
${parameter@*}

${parameter^pattern}
${parameter^^pattern}
${parameter,pattern}
${parameter,,pattern}

${parameter/pattern/string}

${parameter%word}
${parameter%%word}

${parameter#word}
${parameter##word}

${#parameter}

${!name[@]}
${!name[*]}

${!prefix*}
${!prefix@}

${3:+; }

${a}
${a:a}
${a:-a}
${a:.*}
${a[@]}
${a[*]}
${level%%[a-zA-Z]*}
${a[var]}
${a[var+var2]}
${a[var+var2+1]#[a-z][0]}
${#a[1]}
${a-`$[1]`}
${\}}

if [[ -z "${CONTENT[${index}-${index}-${index}]-}" ]]; then
    echo "Example"
fi

$(( ${param} + 1))

msg="Entering $funcname($args)${envstr:+ with environment $envstr${3:+; }}$3"


NEW_USERNAME=${NEW_USERNAME:-builduser}

REPOSITORY="https://repo.labs.intellij.net/cache/${REPOSITORY/https:\//https}"

local wait_seconds="${2:-10}" # 10 seconds as default timeout

OLD_USERNAME=${OLD_USERNAME:-jetbrains}

DISTRIB_MAJOR="${DISTRIB_RELEASE%.*}"

echo "export ${NAME^^}_HOME=$DIR" >/etc/profile.d/$NAME.sh

HOMEBREW_PACKAGES=($HOMEBREW_PACKAGES)
for PKG in ${HOMEBREW_PACKAGES[@]}
do
	/usr/local/bin/brew install $PKG
done

local URL="${3:-https://repo.labs.intellij.net/download/oracle/$FILE}"


for component in ${distrs[*]}
do
    wget -nv "https://repo.labs.intellij.net/download/oracle/${component}"
done

ARGS="--install /usr/bin/java java $JAVA_HOME/bin/java 100"
for i in "${commands[@]}"; do
  ARGS="$ARGS --slave /usr/bin/$i $i $JAVA_HOME/bin/$i"
done

JDK_VER=${JDK_VER:-8.181}

DISTRIB_MAJOR=${DISTRIB_RELEASE%.*}

A=(1 2 3  )

echo $A


// array
declare -A SHA256MAP=( \
        ["11"]="3784cfc4670f0d4c5482604c7c513beb1a92b005f569df9bf100e8bef6610f2e" \
        ["9.0.4"]="39362fb9bfb341fcc802e55e8ea59f4664ca58fd821ce956d48e1aa4fb3d2dec" \
        ["10"]="c851df838a51af52517b74e3a4b251d90c54cf478a4ebed99e7285ef134c3435")

echo "${SHA256MAP[$VERSION]} /tmp/$FILE" | sha256sum -c -

${entry%%[[]]*}
${entry%%[[::]]*}
${entry%%[[:space:]]*}

function addConf {
    reportDebugFuncEntry "$*" "type name"

    typeset entryFound
    typeset addConfEntry

    entry="$*"
    addConfEntry="$entry"
    type="${entry%%[[:space:]]*}"

    # Check for name collision
    entryFound="$(grep -v '^#' "$usrcff" | grep " $name(")"
    if [ "$entryFound" ]; then
        reportError "An entry with name $name is already present in $usrcff"
        return 1
    fi

    # Check for addr collision, excluding services.
    if [ "$type" = "host" -o "$type" = "guest" ]; then
        entryFound="$(grep -v '^#' "$usrcff" | grep -v '^service' | grep -v '^group' | grep "$addr")"
        if [ "$entryFound" ]; then
            reportError "An entry with addr $addr is already present in $usrcff"
            return 1
        fi
    fi

    # Add new entry.
    printf "$addConfEntry\n" >> "$usrcff"
    reportInfo "Successfully added entry $*"
    return 0
}

type="${entry%[[:space:]]*}"

${parameter-word}
${parameter=word}
${parameter?word}
${parameter+word}
${@}

line="${line%$'\r'}"
${parameter:-abc}

line="${line%'\r'}"
line="${line%$'\r'}"
line="${line%"\r"}"

# error on first arg: empty expansion
echo ${} ${line}
echo ${a/}
echo ${a//}
echo ${a%}
echo ${a%%}