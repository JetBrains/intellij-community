# Test Subversion binary compile script
# (1) ./configure ... --enable-static --enable-all-static
# (2) make
# (3) ./mksvn.sh

cd subversion/svn && rm -f svn && gcc -static-libgcc -g -O2 -pthread -Werror=implicit-function-declaration -o svn  add-cmd.o blame-cmd.o cat-cmd.o changelist-cmd.o checkout-cmd.o cleanup-cmd.o commit-cmd.o conflict-callbacks.o copy-cmd.o delete-cmd.o diff-cmd.o export-cmd.o help-cmd.o import-cmd.o info-cmd.o list-cmd.o lock-cmd.o log-cmd.o main.o merge-cmd.o mergeinfo-cmd.o mkdir-cmd.o move-cmd.o notify.o patch-cmd.o propdel-cmd.o propedit-cmd.o propget-cmd.o proplist-cmd.o props.o propset-cmd.o relocate-cmd.o resolve-cmd.o resolved-cmd.o revert-cmd.o status-cmd.o status.o switch-cmd.o tree-conflicts.o unlock-cmd.o update-cmd.o upgrade-cmd.o util.o \
 ../../subversion/libsvn_client/.libs/libsvn_client-1.a \
 ../../subversion/libsvn_ra/.libs/libsvn_ra-1.a \
 ../../subversion/libsvn_ra_local/.libs/libsvn_ra_local-1.a \
 ../../subversion/libsvn_ra_serf/.libs/libsvn_ra_serf-1.a \
 ../../subversion/libsvn_ra_svn/.libs/libsvn_ra_svn-1.a \
 ../../subversion/libsvn_diff/.libs/libsvn_diff-1.a \
 ../../subversion/libsvn_fs/.libs/libsvn_fs-1.a \
 ../../subversion/libsvn_fs_util/.libs/libsvn_fs_util-1.a \
 ../../subversion/libsvn_fs_fs/.libs/libsvn_fs_fs-1.a \
 ../../subversion/libsvn_delta/.libs/libsvn_delta-1.a \
 ../../subversion/libsvn_repos/.libs/libsvn_repos-1.a \
 ../../subversion/libsvn_wc/.libs/libsvn_wc-1.a \
 ../../subversion/libsvn_subr/.libs/libsvn_subr-1.a \
 ../../apr-util/xml/expat/.libs/libexpat.a \
 ../../serf/.libs/libserf-0.a \
 ../../apr-util/.libs/libaprutil-1.a \
 ../../apr/.libs/libapr-1.a \
 /usr/lib/i386-linux-gnu/libz.a \
 /usr/lib/i386-linux-gnu/libssl.a \
 /usr/lib/i386-linux-gnu/librt.a \
 /usr/lib/i386-linux-gnu/libcrypt.a \
 /usr/lib/i386-linux-gnu/libcrypto.a \
 -lc -lpthread -ldl

