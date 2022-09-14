mv -v !(src|*.sh) ${dir}

if ! mv -v !(src|*.sh) ${dir}; then
  echo "Text sample"
fi

echo !(a)
ls -la !(a|b|c|z*)

echo @(a|lib)
ls -la @(a|lib|a*|lib*)