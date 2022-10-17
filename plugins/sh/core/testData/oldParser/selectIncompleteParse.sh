#error markers must be present, but the incomplete if should be parsed without remaining elements
select f in a; do; done
select FILENAME in *;
do
  case $FILENAME in
        "$QUIT")
          echo "Exiting."
          break
          ;;
        *)
          echo "You picked $FILENAME ($REPLY)"
          ;;
  esac
done
select a in; do echo; done

echo "Example text"
