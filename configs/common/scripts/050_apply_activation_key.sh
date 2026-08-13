#!/bin/bash
# Deploy a Free Tier (or Enterprise) activation key and replace any trial license.
#
# IMPORTANT: Liferay Docker entrypoint *sources* these scripts from inside a
# shell function. Do not use `exit` or `return` (both abort container startup).
#
# Sources (first match wins):
#   1. $LIFERAY_ACTIVATION_KEY_XML (ECS / Secrets Manager)
#   2. exactly one activation-key*.xml under /mnt/liferay/activation-key/
#      (local: local/license/)
#
# If multiple activation-key*.xml files are present, nothing is copied and an
# error is logged. If no key is found, existing trial/previous licenses are
# left alone. See README.md (Activation key)

LIFERAY_HOME="${LIFERAY_HOME:-/opt/liferay}"
ACTIVATION_MOUNT="${LIFERAY_ACTIVATION_KEY_DIR:-/mnt/liferay/activation-key}"
DEPLOY_DIR="${LIFERAY_HOME}/deploy"
KEY_DEST="${DEPLOY_DIR}/activation-key.xml"

mkdir -p "${LIFERAY_HOME}/data/license" "${DEPLOY_DIR}" "${LIFERAY_HOME}/osgi/modules"

KEY_SRC=""
KEY_AMBIGUOUS=""

if [ -n "${LIFERAY_ACTIVATION_KEY_XML:-}" ]; then
  printf '%s\n' "${LIFERAY_ACTIVATION_KEY_XML}" > "${KEY_DEST}"
  KEY_SRC="LIFERAY_ACTIVATION_KEY_XML"
else
  key_count=0
  for f in "${ACTIVATION_MOUNT}"/activation-key*.xml; do
    [ -f "$f" ] || continue
    key_count=$((key_count + 1))
  done

  if [ "${key_count}" -eq 1 ]; then
    for f in "${ACTIVATION_MOUNT}"/activation-key*.xml; do
      [ -f "$f" ] || continue
      cp -f "$f" "${KEY_DEST}"
      KEY_SRC="$f"
    done
  elif [ "${key_count}" -gt 1 ]; then
    KEY_AMBIGUOUS=1
    echo "[activation-key] ERROR: Multiple activation-key*.xml files found under ${ACTIVATION_MOUNT}; refusing to choose:"
    for f in "${ACTIVATION_MOUNT}"/activation-key*.xml; do
      [ -f "$f" ] || continue
      echo "[activation-key]   $f"
    done
    echo "[activation-key] Leave exactly one file, or set LIFERAY_ACTIVATION_KEY_XML."
  fi
fi

if [ -n "${KEY_SRC}" ]; then
  echo "[activation-key] Clearing previous license artifacts before deploying key..."
  # Only remove license *files*; keep subdirs such as data/license/server
  find "${LIFERAY_HOME}/data/license" -maxdepth 1 -type f -delete 2>/dev/null || true
  find "${LIFERAY_HOME}/osgi/modules" -maxdepth 1 -type f \( -name '*license*.xml' -o -name '*activation*.xml' \) -delete 2>/dev/null || true
  find "${DEPLOY_DIR}" -maxdepth 1 -type f -name '*trial*license*.xml' -delete 2>/dev/null || true

  chmod 644 "${KEY_DEST}" || true
  echo "[activation-key] Deployed activation key from ${KEY_SRC} -> ${KEY_DEST}"
elif [ -z "${KEY_AMBIGUOUS}" ]; then
  echo "[activation-key] WARNING: No Free Tier activation key found (${ACTIVATION_MOUNT}/activation-key*.xml or LIFERAY_ACTIVATION_KEY_XML)."
  echo "[activation-key] Leaving image trial / existing licenses unchanged."
  echo "[activation-key] See README.md (Activation key)"
fi
