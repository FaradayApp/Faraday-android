#!/bin/bash

set -e

mydir="$(dirname "$(realpath "$0")")"
source "$mydir/merge_helpers.sh"

# Require clean git state
require_clean_git

# Tag this version for easier git diff-ing
versionMajor=`get_prop ext.versionMajor`
versionMinor=`get_prop ext.versionMinor`
versionPatch=`get_prop ext.versionPatch`
tag="sc_last_v$versionMajor.$versionMinor.$versionPatch"
git tag "$tag" || true

# Revert Schildi's upstream string changes
git checkout `upstream_common_base` -- "$mydir/library/ui-strings/src/main/res/**/strings.xml"
git commit -m "Automatic revert to unchanged upstream strings, pt.1"

# Keep in sync with post_merge.sh!
restore_upstream .github
restore_upstream fastlane
restore_upstream README.md

git add -A
git commit -m "[TMP] Automatic upstream merge preparation"
