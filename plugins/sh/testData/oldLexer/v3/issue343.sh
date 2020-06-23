{
<<EOF1 <<EOF2
EOF1
${}
EOF2
}
$1
{
<<EOF3 <<'EOF4'
EOF3
${
EOF4
}
$1
{
<<EOF5 <<'EOF6'
EOF5
$(
EOF6
)
$1
{
<<EOF7 <<'EOF8'
EOF7
$((
EOF8
)
$1
