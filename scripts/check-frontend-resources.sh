#!/usr/bin/env bash
#
# Verifies that Liferay's frontend resources are served correctly when the portal
# runs at a multi-segment context path (/ohjaaja/cms).
#
# Background and root cause: docs/CONTEXT_PATH_REGRESSION.md
#
# Usage:
#   scripts/check-frontend-resources.sh [BASE_URL]
#
# Examples:
#   scripts/check-frontend-resources.sh                      # http://localhost:8080/ohjaaja/cms
#   scripts/check-frontend-resources.sh https://virkailija.jodkehitys.fi/ohjaaja/cms
#   JOD_CMS_CURL_OPTS='-H "Cookie: ..."' scripts/check-frontend-resources.sh <url>
#
# Exits 0 when every resource returns 200, otherwise 1.

set -uo pipefail

BASE_URL="${1:-${JOD_CMS_BASE_URL:-http://localhost:8080/ohjaaja/cms}}"
BASE_URL="${BASE_URL%/}"

# These resources are resolved through HashedFilesRegistry. They are exactly the ones
# that returned 404 on the DXP 2026.Q1 line at a multi-segment context path
# (fixed in 2026.Q2, LPD-76256).
PATHS=(
  "/o/frontend-js-web/Liferay.js"
  "/o/frontend-js-web/__liferay__/legacy.js"
  "/o/portal-url-builder-impl/Liferay.js"
  "/o/oauth2-provider-web/__liferay__/global.js"
  "/o/frontend-js-spa-web/__liferay__/index.js"
  "/o/portal-search-web/__liferay__/search-bar.js"
  "/o/portal-template-react-renderer-impl/__liferay__/index.js"
  "/o/frontend-taglib-clay/__liferay__/index.js"
  "/o/product-navigation-user-personal-bar-web/__liferay__/index.js"
  "/o/layout-taglib/__liferay__/render.js"
  "/o/frontend-js-tooltip-support-web/__liferay__/index.js"
  "/o/frontend-js-bootstrap-support-web/__liferay__/index.js"
  "/o/js/language/en_US/frontend-js-web/all.js"
  "/o/frontend-css-cadmin-web/clay_admin.css"
)

http_status() {
  # shellcheck disable=SC2086
  curl --silent --show-error --location --max-time 30 \
    --output /dev/null --write-out '%{http_code}' \
    ${JOD_CMS_CURL_OPTS:-} "$1" 2>/dev/null || echo "000"
}

echo "Checking: ${BASE_URL}"
echo

root_status="$(http_status "${BASE_URL}/")"
if [ "${root_status}" != "200" ]; then
  echo "ERROR: the portal does not respond at ${BASE_URL}/ (HTTP ${root_status})"
  echo "Start the instance or check the URL before running this."
  exit 1
fi

failed=0
for path in "${PATHS[@]}"; do
  status="$(http_status "${BASE_URL}${path}")"
  if [ "${status}" = "200" ]; then
    printf '  %-4s %s\n' "OK" "${path}"
  else
    printf '  %-4s %s (HTTP %s)\n' "FAIL" "${path}" "${status}"
    failed=$((failed + 1))
  fi
done

echo
if [ "${failed}" -eq 0 ]; then
  echo "All ${#PATHS[@]} resources OK."
  exit 0
fi

echo "${failed}/${#PATHS[@]} resources failed."
echo "If these are 404s, the context path regression is back."
echo "See docs/CONTEXT_PATH_REGRESSION.md."
exit 1
