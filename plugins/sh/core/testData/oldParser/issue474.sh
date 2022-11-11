
cat <<EOF
This block has no syntax error.
EOF

cat <<EOF;
This block also should have no syntax error.
But causes an error at the beginning of the line of "This block also..."
EOF