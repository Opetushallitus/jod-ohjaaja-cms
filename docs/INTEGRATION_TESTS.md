# Integration tests

The `modules/*-test` Gradle modules contain JUnit 4 tests that run inside a real Liferay OSGi
runtime in Docker, using Liferay's own
[Arquillian JUnit bridge](https://github.com/liferay/liferay-portal/tree/master/modules/test/arquillian-extension-junit-bridge).

## How it works

```text
 liferay-test-container (image liferay-test, Dockerfile.test)
 ┌──────────────────────────────────────────────────────────────────────────┐
 │  Liferay JVM                            test JVM (docker exec)           │
 │  ┌──────────────────────────┐           ┌────────────────────────────┐   │
 │  │ Arquillian connector     │◀── 1 ─────│ JUnit console launcher     │   │
 │  │ 127.0.0.1:32763          │  install  │ + @RunWith(Arquillian)     │   │
 │  │                          │  bundle   │                            │   │
 │  │ test bundle (per module) │── 2 ─────▶│ result socket              │   │
 │  │                          │  results  │ 127.0.0.1:32764+           │   │
 │  └──────────────────────────┘           └────────────────────────────┘   │
 └──────────────────────────────────────────────────────────────────────────┘
```

The bridge builds an OSGi bundle from the module's test classes with bnd, installs it through the
connector bundle, and the tests stream their results back. Both ends bind to loopback only, which
Docker cannot publish, so the test JVM runs inside the container with `docker exec` on the JDK the
Liferay image ships. The project directory and the Gradle user home are mounted into the
container, and the classpath is translated to those mounts. This works the same on Linux, macOS
and Windows, and the container publishes no ports, so it can run next to the local development
stack.

## Running

```bash
./gradlew testWithDockerContainer
```

Builds the `liferay-test` image with all application modules, starts the container, runs the
`integrationTest` task of every `modules/*-test` module and removes the container. A failing module
does not stop the others; the build fails at the end if any module failed. Results are printed on
the console, per-module XML goes to `modules/<module>/build/test-results/integrationTest/`, and the
`integrationTestReport` task merges them into `build/reports/integration-tests/index.html`.

### Against a running container

With the container already running (`./gradlew dockerBuildTestImage dockerRunTestContainer`), a
single module or only some test classes can be run:

```bash
./gradlew :modules:jod-ohjaaja-cms-tags-test:integrationTest
./gradlew :modules:jod-ohjaaja-cms-auth-test:integrationTest -PintegrationTests=ContentProducerAccessTest,RoleSynchronizerTest
```

`-PintegrationTests` also works with `testWithDockerContainer`; modules without a selected class
are skipped, and an unknown class name fails the build right away. Changed test classes only need
the task to be re-run, but the application modules are baked into the image, so rebuild it after
changing them.

The tests cannot be run from an IDE or with Gradle's `test` task: both would run outside the
container's loopback. The `test` task is disabled in these modules.

## Writing a test

```java
@RunWith(Arquillian.class)
public class MyServiceTest {
  @ClassRule @Rule public static final LiferayIntegrationTestRule rule = new LiferayIntegrationTestRule();

  @Inject private static MyService myService;

  @Test
  public void something() { /* runs inside Liferay */ }
}
```

- Class names must end with `Test`; all `*Test` classes of a module go into one test bundle.
- Get OSGi services with `@Inject` (`com.liferay.portal.test.rule.Inject`); OSGi's `@Reference`
  does not work in test classes. `@Inject` ignores `service.ranking`, so a mock registered with a
  higher ranking must be looked up through the bundle context.
- A module may add bnd instructions for the test bundle in `integration-test.bnd`, e.g. to embed a
  library that Liferay does not provide (see `jod-ohjaaja-cms-search-test`).

A new module needs `apply from: rootProject.file('gradle/jod.integration-test.gradle')` in its
`build.gradle`, module-specific dependencies as `testImplementation`, and an `include` in
`settings.gradle`. The test library versions are pinned in `gradle/libs.versions.toml`; the Liferay
BOM does not manage them, so re-check them on every Liferay version bump.
