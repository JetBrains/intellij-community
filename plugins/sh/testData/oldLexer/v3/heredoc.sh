cat <<"EOF"  ddf d
[Unit]
After=add-localhost.service
EOF

cat <<'EOF'  ddf d
[Unit]
After=add-localhost.service
EOF

cat <<END
END
cat <<END;
END
cat <<END&& test
END
cat <<END && test
END
cat <<END || test
END
cat <<        END
cat <<        "END"
cat <<        "END""END"
cat <<        $"END""END"
cat <<        $"END"$"END"
cat <<'END'
cat <<        'END'
cat <<        $'END'
cat <<        $'END''END'
cat <<END
cat <<END
cat <<END
ABC
cat <<END
ABC

cat <<END
ABC
cat <<END
ABC
DEF
END
cat << END
ABC
DEF
END
cat <<-END
ABC
DEF
END
cat <<- END
ABC
DEF
END
cat <<END
ABC
DEF
END
cat <<END
ABC
DEF


XYZ DEF
END
cat <<!
!
{
cat <<EOF
test
EOF
}
cat <<EOF
$test
EOF
{
cat <<EOF
$(echo)
EOF
}
if test; then
cat <<EOF
EOF
test
fi
cat <<X
X
cat <<X2
X2
cat <<$
$
cat <<$_X
$_X
cat <<-EOF
EOF
cat <<- EOF
EOF
cat <<-EOF
	EOF
cat <<- EOF
	EOF
cat <<EOF
	EOF
EOF
{
<<EOF <<EOF2
EOF
$(echo a)
EOF2
}
{
<<EOF <<EOF2
EOF
$((a))
EOF2
}
{
<<EOF <<EOF2
EOF
$[a]
EOF2
}