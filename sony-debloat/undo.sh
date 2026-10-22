#!/usr/bin/env bash
# Restore anything debloat.sh touched.
#
#   ./undo.sh              # re-enable CORE tier
#   ./undo.sh --all        # re-enable CORE + OPTIONAL tier
#   ./undo.sh --list       # show what would be re-enabled
#
# pm enable --user 0 <pkg> restores a package that was only disabled.
# If a package was fully uninstalled for user 0 instead, use:
#   adb -s 192.168.0.51:5555 shell pm install-existing <pkg>
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONF="$HERE/packages.conf"
TV="${TV:-192.168.0.51:5555}"

ALL=0
LISTONLY=0
while [ $# -gt 0 ]; do
  case "$1" in
    --all) ALL=1 ;;
    --list) LISTONLY=1 ;;
    -h|--help) sed -n '2,11p' "$0"; exit 0 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
  shift
done

adb connect "$TV" >/dev/null 2>&1 || true
serial=$(adb devices | awk -v t="${TV%%:*}" '$1 ~ t {print $1; exit}')
if [ -z "$serial" ]; then echo "!! cannot reach $TV" >&2; exit 1; fi
run() { adb -s "$serial" "$@"; }

targets() {
  if [ "$ALL" -eq 1 ]; then awk -F'\t' '$1!~/^#/ && NF>=2 {print $2}' "$CONF"
  else awk -F'\t' '$1!~/^#/ && $1=="CORE" && NF>=2 {print $2}' "$CONF"; fi
}

if [ "$LISTONLY" -eq 1 ]; then
  awk -F'\t' 'NF>=3 && $1 !~ /^#/ {printf "%-52s %s\n", $2, $1}' "$CONF"
  exit 0
fi

echo "== re-enabling =="
ok=0; skip=0; fail=0
while IFS=$'\t' read -r pkg note; do
  out=$(run shell pm enable --user 0 "$pkg" 2>&1 || true)
  case "$out" in
    *Enabled*|*enabled*) echo "  OK       $pkg"; ok=$((ok+1)) ;;
    *) echo "  SKIP     $pkg  -> $out"; skip=$((skip+1)) ;;
  esac
done <<< "$(targets)"

echo
echo "re-enabled=$ok skipped=$fail"
echo "reboot: adb -s $serial reboot"
