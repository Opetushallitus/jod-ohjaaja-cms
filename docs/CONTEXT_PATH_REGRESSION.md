# Multi-segment context path and frontend resources

JOD CMS runs at the context path `/ohjaaja/cms` (`webapps/ohjaaja#cms` in Tomcat).
That is a **multi-segment** path, i.e. it contains a slash. The Liferay DXP 2026.Q1
line does not support it and breaks part of the frontend resources.

## Symptom

Pages render, but the browser gets a 404 for some CSS and JS files. The Liferay log
shows lines like:

```text
WARN [code_jsp:168] {code="404", uri=/ohjaaja/cms/o/frontend-js-web/__liferay__/legacy.js}
```

What breaks is specifically the resources resolved through the hashed-files architecture:

- `/o/<bundle>/__liferay__/*.js`
- `/o/frontend-js-web/Liferay.js`, `/o/portal-url-builder-impl/Liferay.js`
- `/o/js/language/<locale>/<bundle>/all.js`
- `/o/frontend-css-cadmin-web/clay_admin.css`

Ordinary static bundle resources (`/o/<bundle>/css/main.(hash).css`), the combo servlet
and the JAX-RS endpoints (`/o/api`, `/o/headless-delivery/*`) keep working — they do not
use the same resolution path. That is why the failure looks random.

## Root cause

`HashedFilesRegistryImpl.getResource()` maps an unhashed request URI to the hashed file
inside the bundle. In 2026.Q1 it **counts path segments** and assumes the context path is
exactly one segment:

```java
int subpathIndex = 3;
if (!contextPath.isEmpty()) {
    subpathIndex = 4;
}
_serviceTrackerMap.getService(merge(pathParts.subList(0, subpathIndex), "/"));
```

For `/ohjaaja/cms/o/frontend-js-web/__liferay__/legacy.js` the lookup key becomes
`/ohjaaja/cms/o` — the bundle name is dropped. The lookup returns `null`, the handler
declines, and the request falls through to the portal's 404 page.

| Context path | Result |
| --- | --- |
| ROOT (`/`) | works |
| `/cms` (single segment) | works |
| `/ohjaaja/cms` (multi-segment) | **broken** |

## Fix

Liferay removed the segment counting and replaced it with length-based parsing
(the new `FrontendJSWebUtil`):

- Commit `8c3ba3e921`, 2026-03-13, ticket **LPD-76256**
  *"Optimize, test and clean up the string handling mess"*
- First shipped in **2026.Q2.0**. Not present in any `2026.q1.x` release.

Verified by bisection: `2026.q1.10` and `2026.q1.12` broken; `2026.q2.5`, `2026.q2.8` and
`2026.q2.12` working.

The bug was introduced by commit `81418af721` (2025-10-10, LPD-68182), which added the
`subpathIndex` logic and fixed the single-segment case. Before that, any non-root context
path was broken. Liferay CE 7.4 GA132 worked because the hashed-files architecture did not
exist yet.

## What this means for version selection

**Do not go below 2026.Q2.** There is no release between GA132 and 2026.Q2.0 where a
multi-segment context path works. In particular, moving back to the 2026.Q1 LTS line breaks
the CMS again unless LPD-76256 has been backported there by then.

The alternative would be a single-segment context path (e.g. `/ohjaaja-cms`), but that would
change every public path, the ALB rules and the URLs Liferay embeds in headless-delivery
responses.

## Regression check

Run after every version bump:

```bash
scripts/check-frontend-resources.sh http://localhost:8080/ohjaaja/cms
```

The script checks every known-fragile resource and exits non-zero if any of them does not
return 200. Run it on **every** Liferay version bump before the change moves forward.
