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


if [[ -z "${CONTENT[${index}-${index}-${index}]-}" ]]; then
    echo "Example"
fi

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


// array
declare -A SHA256MAP=( \
        ["11"]="3784cfc4670f0d4c5482604c7c513beb1a92b005f569df9bf100e8bef6610f2e" \
        ["9.0.4"]="39362fb9bfb341fcc802e55e8ea59f4664ca58fd821ce956d48e1aa4fb3d2dec" \
        ["10"]="c851df838a51af52517b74e3a4b251d90c54cf478a4ebed99e7285ef134c3435")

type="${entry[[:space:]]*}"

${parameter-word}
${parameter=word}
${parameter?word}
${parameter+word}
${@}

${parameter:-{}

line="${line%'\r'}"
line="${line%$'\r'}"
line="${line%"\r"}"

# IDEA-256714
sp="${base##*[![:space:]]}"