# JOD Ohjaaja CMS

Part of the [Digital Service Ecosystem for Continuous Learning (JOD) project](https://wiki.eduuni.fi/pages/viewpage.action?pageId=404882394).

---

Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
The Ministry of Economic Affairs and Employment, The Finnish National Agency of
Education (Opetushallitus) and The Finnish Development and Administration centre
for ELY Centres and TE Offices (KEHA).

Licensed under the European Union Public Licence EUPL-1.2 or later.

---

## Getting Started

This is a Liferay Workspace for **Liferay DXP Free Tier**.

### Prerequisites

- Java 21
- Gradle
- Docker
- Docker Compose

### Activation key

Free Tier needs an XML activation key from [Liferay Marketplace](https://marketplace.liferay.com/p/liferay-dxp-free-tier) (**Liferay DXP – Free Tier** → **Get Activation Key**). Do not commit the key to git.

| Environment | Where the key goes |
| --- | --- |
| Local | Copy to `local/license/` as `activation-key*.xml` (exactly one file) |
| AWS (dev / test / prod) | Injected automatically at container start via the ECS task definition (`LIFERAY_ACTIVATION_KEY_XML`) |

### Local development

```sh
./gradlew clean buildDockerImage
docker compose up
```

`buildDockerImage` syncs `.env` (`LIFERAY_DXP_TAG`) from `gradle.properties` automatically.

When the environment is running, deploy module changes without a restart:

```sh
./gradlew deploy
```

### One-shot database upgrade (local)

To run a database upgrade against a DB copy, temporarily uncomment in [docker-compose.yml](docker-compose.yml):

```yaml
- LIFERAY_UPGRADE_PERIOD_DATABASE_PERIOD_AUTO_PERIOD_RUN=true
```

Then `docker compose up` once, and comment the line back out for normal use.

Schema upgrades are permanent — snapshot or copy the database first. Never use `docker compose down -v` or delete `local/data` to clear a startup error; both destroy local data permanently.

```sh
docker exec postgres1 pg_dump -U liferay -Fc lportal > lportal-$(date +%Y%m%d).dump
tar -czf local-data-$(date +%Y%m%d).tar.gz local/data
```

---

## Liferay version

### Sources of truth

| What | Where |
| --- | --- |
| Liferay product + Docker base image | [`gradle.properties`](gradle.properties) (`liferay.workspace.product`, `liferay.workspace.docker.image.liferay`) |
| Shared library / plugin versions | [`gradle/libs.versions.toml`](gradle/libs.versions.toml) |

Confirm the target tag is still Free Tier–eligible on [Community Downloads](https://www.liferay.com/downloads-community) before upgrading.

### Bumping the version

1. Update `liferay.workspace.product` and `liferay.workspace.docker.image.liferay` in `gradle.properties` (keep them aligned).
2. Locally: `./gradlew clean buildDockerImage` (or `./gradlew syncDockerEnv` if you only need `.env` updated), then verify with `docker compose up` / tests.
3. Merge to `main`. The build packages an image from the new Docker base. While `gradle.properties` differs from GitHub variable `JOD_LIFERAY_DOCKER_IMAGE`, `upgrade_check` **skips** normal auto-deploy.
4. Run workflow **`upgrade_liferay`** per environment (**dev → test → production**). It snapshots Aurora, deploys with DB auto-upgrade enabled, and waits for stability.
5. After all environments are on the new image, set `JOD_LIFERAY_DOCKER_IMAGE` to the same value as `liferay.workspace.docker.image.liferay` so normal `deploy` jobs run again.

See also [docs/WORKFLOW_METRICS.md](docs/WORKFLOW_METRICS.md) for the known Free Tier Workflow Metrics startup exception and mitigation.

---

## Testing

### Running all integration tests

```sh
./gradlew testWithDockerContainer
```

This builds and starts a Liferay test container, runs all modules ending with `-test`, writes a unified HTML report to `build/reports/unified-tests/index.html`, and cleans up afterwards.

### Generate unified test report

```sh
./gradlew unifiedTestReport
```

### Adding new test modules

1. Create a module under `modules/` whose name ends with `-test` (e.g. `modules/jod-ohjaaja-cms-myfeature-test/`).
2. It is discovered and included automatically.

For detailed testing documentation, see [test/ARQUILLIAN_README.md](test/ARQUILLIAN_README.md).
