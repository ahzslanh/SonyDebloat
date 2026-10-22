#!/usr/bin/env bash
# Sony BRAVIA VU1 (Google TV) debloat - apply tier
#
#   ./debloat.sh                 # dry run (default, changes nothing)
#   ./debloat.sh --apply         # disable CORE tier
#   ./debloat.sh --apply --all   # disable CORE + OPTIONAL tier
#   ./debloat.sh --list          # show what would be touched
#
# Nothing here needs root, nothing touches the bootloader, everything is
# reversible with ./undo.sh  (pm enable --user 0 <pkg>).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONF="$HERE/packages.conf"
SNAP="$HERE/snapshot"

TV="${TV:-192.168.0.51:5555}"
APPLY=0
ALL=0
LISTONLY=0

while [ $# -gt 0 ]; do
  case "$1" in
    --apply) APPLY=1 ;;
    --all) ALL=1 ;;
    --list) LISTONLY=1 ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
  shift
done

adb connect "$TV" >/dev/null 2>&1 || true
if ! adb devices | grep -q "${TV%%:*}"; then
  echo "!! cannot reach $TV  (adb devices shows nothing)" >&2
  exit 1
fi

serial=$(adb devices | awk -v t="${TV%%:*}" '$1 ~ t {print $1; exit}')
run() { adb -s "$serial" "$@"; }

targets() {
  if [ "$ALL" -eq 1 ]; then
    awk -F'\t' '$1 ~ /^(CORE|OPTIONAL|RAM)$/ && NF>=3 {print $2 "\t" $3}' "$CONF"
  elif [ "$RAM" -eq 1 ]; then
    awk -F'\t' '$1=="RAM" && NF>=3 {print $2 "\t" $3}' "$CONF"
  else
    awk -F'\t' '$1=="CORE" && NF>=3 {print $2 "\t" $3}' "$CONF"
  fi
}

if [ "$LISTONLY" -eq 1 ]; then
  printf '%-52s %s\n' "PACKAGE" "TIER/NOTE"
  awk -F'\t' 'NF>=3 && $1 !~ /^#/ {printf "%-52s %s\n", $2, $1" - "$3}' "$CONF"
  exit 0
fi

echo "== device =="
run shell getprop ro.product.model
run shell getprop ro.build.version.release
echo

mkdir -p "$SNAP"
ts=$(date +%Y%m%d-%H%M%S)
echo "== pre-state snapshot: snapshot/pre-$ts =="
mkdir -p "$SNAP/pre-$ts"
run shell pm list packages -d > "$SNAP/pre-$ts/disabled.txt"
run shell 'ps -A -o USER,PID,RSS,NAME' | sort -k3 -rn | head -40 > "$SNAP/pre-$ts/ps-top.txt"
run shell dumpsys meminfo --local > "$SNAP/pre-$ts/meminfo.txt"
run shell df -h > "$SNAP/pre-$ts/df.txt"

if [ "$APPLY" -eq 0 ]; then
  echo "== DRY RUN (nothing changed) =="
  disabled_now=$(run shell pm list packages -d)
  n=0
  while IFS=$'\t' read -r pkg note; do
    if printf '%s\n' "$disabled_now" | grep -qx "package:$pkg"; then already=DISABLED; else already=active; fi
    printf "  would disable %-50s [%s] %s\n" "$pkg" "$already" "$note"
    n=$((n+1))
  done <<< "$(targets)"
  echo
  echo "$n package(s). Re-run with --apply (add --all to include OPTIONAL)."
  exit 0
fi

echo "== applying =="
ok=0; skip=0; fail=0
while IFS=$'\t' read -r pkg note; do
  out=$(run shell pm disable-user --user 0 "$pkg" 2>&1 || true)
  case "$out" in
    *Disabled*) echo "  OK       $pkg  ($note)"; ok=$((ok+1)) ;;
    *already\ disabled*) echo "  SKIP     $pkg  (already disabled)"; skip=$((skip+1)) ;;
    *disabled-user*) echo "  SKIP     $pkg  (already disabled)"; skip=$((skip+1)) ;;
    *new\ state:\ disabled*) echo "  SKIP     $pkg  (already disabled)"; skip=$((skip+1)) ;;
    *) echo "  FAIL     $pkg  -> $out"; fail=$((fail+1)) ;;
  esac
done <<< "$(targets)"

echo
echo "disabled=$ok already=$skip failed=$fail"
echo "reboot to reclaim the RAM:   adb -s $serial reboot"
echo "rollback:                    ./undo.sh"
