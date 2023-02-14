#!/bin/bash
find_decompressor() {
    set +x;
    echo " fd: \$FNAME=${FNAME}";
    case "$FNAME" in
        *.+(z|Z|gz))    echo "match .gz"; FNAME="${FNAME:%.+(z|Z|gz)}"; echo "after fname"; DECOMPRESSOR="gzip -dc"; echo "after decompresor" ;;
        *.bz2)          DECOMPRESSOR="bzip2 -dc" ;;
        *.xz|*.lzma)    DECOMPRESSOR="xz -dc" ;;
    esac;
    echo "~fd: \$FNAME=${FNAME}";
}