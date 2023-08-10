#!/bin/bash

RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$USER}/gaudi"
CONFIG_FILE="${XDG_CONFIG_HOME:-$HOME/.config}/gaudi.config"
CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/gaudi"
MAVEN_REPOSITORY="https://repo1.maven.org/maven2"

if test -f "$CONFIG_FILE"; then
    . "$CONFIG_FILE"
fi

BUILD_DIR=build
FINGERPRINT_FILE="$BUILD_DIR/fingerprint"
SOURCES_DIR="src"
RESOURCES_DIR="resources"
COMPILE_ONLY_RESOURCES_DIR="compile-only-resources"
RUNTIME_ONLY_RESOURCES_DIR="run-only-resources"
CLASSES_DIR="$BUILD_DIR/classes"
GENERATED_DIR="$BUILD_DIR/generated-sources/javac"
DEPENDENCIES_FILE="$BUILD_DIR/dependencies.lock"

if test -f build-info.sh; then
    . ./build-info.sh
fi

SELF="$0"

case "$1" in
    init)
        NAME="$2"
        if test -z "$ROOT_PACKAGE_NAME"; then
            echo "Specify ROOT_PACKAGE_NAME in $CONFIG_FILE file" >&2
            exit 2
        fi
        PACKAGE_NAME="$ROOT_PACKAGE_NAME.$(echo "$NAME" | sed 's/[._-]//g')"
        mkdir $NAME
        mkdir $NAME/resources
        PACKAGE_DIR="$NAME/src/$(echo "$PACKAGE_NAME" | sed 's%\.%/%g')"
        mkdir -p "$PACKAGE_DIR"
        (
            echo "package $PACKAGE_NAME;"
            echo
            echo "public class Main {"
            echo "    public static void main(String[] args) {"
            echo '        System.out.println("Hello, World");'
            echo "    }"
            echo "}"
        ) > "$PACKAGE_DIR/Main.java"
        echo "MAIN_CLASS=$PACKAGE_NAME.Main" > $NAME/build-info.sh
        ;;
    resolve)
        echo "Manual resolution is required: create $DEPENDENCIES_FILE file manually"
        echo "dependencies.lock format is as follows:"
        echo
        echo ARTIFACT1_NAME ARTIFACT1_VERSION
        echo ARTIFACT2_NAME ARTIFACT2_VERSION
        echo ...
        echo ARTIFACT_N_NAME ARTIFACT_N_VERSION
        echo
        echo "For example junit launcher can be specified like:"
        echo
        echo org.junit.platform.junit-platform-console-standalone 1.0.0
        ;;
    download)
        URL="$2"
        FILE="$3"
        echo "$(date '+%Y-%m-%dT%H:%M:%S') Downloading $URL ..." >&2
        curl -fs "$URL" > "$FILE.part"
        if test "$?" -eq 0; then
            mv "$FILE.part" "$FILE"
            echo "$(date '+%Y-%m-%dT%H:%M:%S') Downloading $URL ... done." >&2
            exit 0
        else
            echo "Error downloading: $URL" >&2
            echo "$(date '+%Y-%m-%dT%H:%M:%S') Downloading $URL ... error." >&2
            exit 2
        fi
        ;;
    artifact-versions)
        ARTIFACT="$2"
        ARTIFACT_AS_PATH="$(echo "$ARTIFACT" | sed 's%\.%/%g')"
        TEMPFILE="$(mktemp --suffix=.xml)"
        curl -s "$MAVEN_REPOSITORY/$ARTIFACT_AS_PATH/maven-metadata.xml" > "$TEMPFILE"
        echo "cat metadata/versioning/versions/version/text()" \
            | xmllint -shell "$TEMPFILE" 2>/dev/null \
            | sed '/^ -------$/d;/^\/ >  -------$/d;/^\/ > $/d'
        rm "$TEMPFILE"
        ;;
    artifact-file)
        ARTIFACT="$2"
        VERSION="$3"
        mkdir -p "$CACHE_DIR"
        ARTIFACT_AS_PATH="$(echo "$ARTIFACT" | sed 's%\.%/%g')"
        ARTIFACT_BASE_NAME=$(basename "$ARTIFACT_AS_PATH")
        ARTIFACT_RELATIVE_PATH="$ARTIFACT_AS_PATH/$VERSION/$ARTIFACT_BASE_NAME-$VERSION.jar"
        ARTIFACT_FILE="$CACHE_DIR/$ARTIFACT_RELATIVE_PATH"
        if test -f "$ARTIFACT_FILE"; then
            echo "$ARTIFACT_FILE"
        else
            URL="$MAVEN_REPOSITORY/$ARTIFACT_RELATIVE_PATH"
            mkdir -p "$(dirname "$ARTIFACT_FILE")"
            mkdir -p "$RUNTIME_DIR"
            flock "$RUNTIME_DIR/lock" "$SELF" download "$URL" "$ARTIFACT_FILE" && echo "$ARTIFACT_FILE"
        fi
        ;;
    classpath)
        if ! test -f $DEPENDENCIES_FILE; then
            echo "WARNING: Classpath is empty: Does not exist: $DEPENDENCIES_FILE" >&2
            exit 0
        else
            cat "$DEPENDENCIES_FILE" | (
                CLASSPATH=""
                while read ARTIFACT VERSION; do
                    ARTIFACT_FILE="$($SELF artifact-file $ARTIFACT $VERSION)"
                    if ! test "$?" -eq 0; then
                        exit 2
                    fi
                    if test -n "$CLASSPATH"; then
                       CLASSPATH="$CLASSPATH:$ARTIFACT_FILE"
                    else
                       CLASSPATH="$ARTIFACT_FILE"
                    fi
                done
                echo "$CLASSPATH"
            )
        fi
        ;;
    compile-classpath)
        CLASSPATH="$("$SELF" classpath)"
        RESULT="$?"
        if ! test "$RESULT" -eq 0; then
            exit "$RESULT"
        else
            if test -d "$COMPILE_ONLY_RESOURCES_DIR"; then
                if test -n "$CLASSPATH"; then
                    CLASSPATH="$COMPILE_ONLY_RESOURCES_DIR:$CLASSPATH"
                else
                    CLASSPATH="$COMPILE_ONLY_RESOURCES_DIR"
                fi
            fi
            echo "$CLASSPATH"
        fi
        ;;
    compile)
        CLASSPATH="$("$SELF" compile-classpath)"
        RESULT="$?"
        if ! test "$RESULT" -eq 0; then
            exit "$RESULT"
        else
            if ! test -d "$SOURCES_DIR"; then
                echo "Sources directory does not exist: $SOURCES_DIR" >&2
                exit 2
            fi

            echo "======================================================================================"
            echo "                                     COMPILING                                        "
            echo "======================================================================================"
            echo

            SOURCES="$(find "$SOURCES_DIR" -type f -name '*.java' | sort)"
            RESOURCES=""
            if test -d "$RESOURCES_DIR"; then
                RESOURCES="$(find "$RESOURCES_DIR" -type f | sort)"
            fi
            for D1 in "$RESOURCES_DIR" "$RUN_ONLY_RESOURCES_DIR" "$COMPILE_ONLY_RESOURCES_DIR"; do
                for D2 in "$RESOURCES_DIR" "$RUN_ONLY_RESOURCES_DIR" "$COMPILE_ONLY_RESOURCES_DIR"; do
                    if test "$D1" '!=' "$D2" && test -d "$D1" && test -d "$D2"; then
                        LIST1=$(mktemp)
                        LIST2=$(mktemp)
                        BEFORE_EXIT=(rm "$LIST1" "$LIST2")
                        find "$D1" -type f -printf '%P\n' | sort > "$LIST1"
                        find "$D2" -type f -printf '%P\n' | sort > "$LIST2"

                        if test "$(join "$LIST1" "$LIST2" | wc -l)" -gt 0; then
                            echo "ERROR: You have same entries in $RESOURCES_DIR and $COMPILE_ONLY_RESOURCES_DIR." >&2
                            echo "       Every resource should be present in one and only one directory" >&2
                            echo >&2
                            join "$LIST1" "$LIST2" | sed 's/^/        /g'
                            "${BEFORE_EXIT[@]}"
                            exit 2
                        fi
                        "${BEFORE_EXIT[@]}"
                    fi
                done
            done

            for D in "$RESOURCES_DIR" "$COMPILE_ONLY_RESOURCES_DIR"; do
                ANNOTATION_PROCESSOR_DISCOVERY_FILE="$META-INF/services/javax.annotation.processing.Processor"
                if test -f "$D/$ANNOTATION_PROCESSOR_DISCOVERY_FILE"; then
                    echo "ERROR: compilation is impossible with annotation processors service discovery file: $D/$ANNOTATION_PROCESSOR_DISCOVERY_FILE" >&2
                    echo "       Move it to run time only resources directory for compilation to succeed: $RUN_ONLY_RESOURCES_DIR/$ANNOTATION_PROCESSOR_DISCOVERY_FILE" >&2
                    exit 2
                fi
            done

            FINGERPRINT="$(sha1sum $SOURCES $RESOURCES | sha1sum | awk '{print $1}')"
            if test -f "$FINGERPRINT_FILE"; then
                OLD_FINGERPRINT="$(cat "$FINGERPRINT_FILE")"
                if test "$FINGERPRINT" '=' "$OLD_FINGERPRINT"; then
                    echo "Up to date!"
                    exit 0
                fi
                rm "$FINGERPRINT_FILE"
            fi

            rm -rf "$CLASSES_DIR"
            rm -rf "$GENERATED_DIR"
            mkdir -p "$CLASSES_DIR"
            mkdir -p "$GENERATED_DIR"
            if test -d "$RESOURCES_DIR"; then
                cp -r "$RESOURCES_DIR"/* "$CLASSES_DIR"
            fi

            javac \
                -cp "$CLASSPATH" \
                -sourcepath '' \
                -encoding UTF-8 \
                -source 8 \
                -target 8 \
                -Werror \
                -Xlint:all \
                -d "$CLASSES_DIR" \
                -s "$GENERATED_DIR" \
                    $SOURCES
            RESULT="$?"

            if test -d "$RUN_ONLY_RESOURCES_DIR"; then
                cp -r "$RUN_ONLY_RESOURCES_DIR"/* "$CLASSES_DIR"
            fi
            echo "$FINGERPRINT" > "$FINGERPRINT_FILE"

            echo
            echo
            echo

            exit "$RESULT"
        fi
        ;;
    run-classpath)
        CLASSPATH="$("$SELF" classpath)"
        RESULT="$?"
        if ! test "$RESULT" -eq 0; then
            exit "$RESULT"
        else
            if test -n "$CLASSPATH"; then
                CLASSPATH="$CLASSES_DIR:$CLASSPATH"
            else
                CLASSPATH="$CLASSES_DIR"
            fi
            echo "$CLASSPATH"
        fi
        ;;
    run)
        if test -z "$MAIN_CLASS"; then
            echo MAIN_CLASS is not specified. Please specify main class in build-info.sh >&2
            exit 2
        else
            $SELF compile && (
                CLASSPATH="$("$SELF" run-classpath)"               
                RESULT="$?"
                if ! test "$RESULT" -eq 0; then
                    exit "$RESULT"
                else
                    echo "======================================================================================"
                    echo "                                     RUNNING                                          "
                    echo "======================================================================================"
                    echo

                    java -cp "$CLASSPATH" "$MAIN_CLASS"

                    RESULT="$?"
                    echo
                    echo
                    echo
                    exit "$RESULT"
                fi
            )
        fi
        ;;
esac
