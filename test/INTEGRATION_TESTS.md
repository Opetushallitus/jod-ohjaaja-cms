# Integration tests

This directory contains the test-runner infrastructure that lets `*-test` Gradle
modules execute JUnit tests inside a real Liferay OSGi runtime running in
Docker.

## Modules

| Module                                                      | Where it runs                | Purpose                                                                                |
| ----------------------------------------------------------- | ---------------------------- | -------------------------------------------------------------------------------------- |
| [`jod-cms-testrunner-osgi`](./jod-cms-testrunner-osgi/)     | Inside Liferay (OSGi bundle) | Exposes HTTP endpoints that install test bundles and dispatch JUnit runs.              |
| [`jod-cms-testrunner-client`](./jod-cms-testrunner-client/) | Gradle test JVM              | JUnit `Runner` + bnd-based test-bundle builder + HTTP client used by `*-test` modules. |


## HTTP protocol

`TestRunnerServlet` is registered at the OSGi HTTP whiteboard with pattern
`/jod-testrunner/*`. Liferay serves it under `/o/jod-testrunner/*`.

| Method | Path                              | Body                                                             | Response                                                      |
| ------ | --------------------------------- | ---------------------------------------------------------------- | ------------------------------------------------------------- |
| `GET`  | `/o/jod-testrunner/ping`          | -                                                                | `{"status":"ok"}`                                             |
| `POST` | `/o/jod-testrunner/bundles`       | Raw OSGi bundle JAR                                              | `{"bundleId": <long>}`                                        |
| `DELETE` | `/o/jod-testrunner/bundles/{id}`| -                                                                | `204 No Content`                                              |
| `POST` | `/o/jod-testrunner/run`           | JITEF: `{"bundleId":N,"className":"...","filteredMethods":[...]}` | NDJITEF stream (one event per line, see below) |

### Streaming events (`POST /run`)

The endpoint streams events back as NDJITEF using HTTP chunked transfer
encoding. The host parses one line at a time and dispatches each event to the
JUnit `RunNotifier`.

```text
{"type":"started","class":"...","method":"..."}
{"type":"finished","class":"...","method":"..."}
{"type":"failure","class":"...","method":"...","throwableClass":"...","message":"...","stack":"..."}
{"type":"assumptionFailure","class":"...","method":"...","throwableClass":"...","message":"...","stack":"..."}
{"type":"ignored","class":"...","method":"..."}
{"type":"runError","throwableClass":"...","message":"..."}   // only on dispatch-level errors
```

End-of-run is signalled by closing the response stream, so there is no
explicit summary event.

Stack traces are sent as plain text, not as serialised `Throwable` objects.

## Writing a test

In a `*-test` module:

```java
import fi.okm.jod.ohjaaja.cms.testrunner.client.JodInContainerRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JodInContainerRunner.class)
public class MyServiceTest {
  @Test
  public void something() { /* runs inside Liferay */ }
}
```

In the module's `build.gradle`:

```gradle
dependencies {
  testImplementation project(':test:jod-cms-testrunner-client')
  testImplementation "junit:junit:4.13.2"
  testImplementation "com.liferay.portal:com.liferay.portal.test:28.0.0"
  // ...
}

test {
  useJUnit()
  systemProperty "jod.testrunner.url", System.getProperty("jod.testrunner.url", "http://localhost:8080")
}
```

The default Liferay base URL is `http://localhost:8080`. Override with
`-Djod.testrunner.url=...` on the Gradle command line if needed.

## Running

The standard workflow is:

```bash
./gradlew testWithDockerContainer
```

This task:

1. Builds the `jod-cms-testrunner-osgi` bundle and downloads
   `com.liferay.portal.test`.
2. Builds a custom Liferay Docker image (`Dockerfile.test`) that copies the
   OSGi bundle and every application module into `/opt/liferay/osgi/modules`.
3. Starts the container, waits for Liferay to become ready.
4. Runs the `*:test` task of every `modules/*-test/` project against the live
   container.
5. Stops and removes the container.
6. Generates a unified HTML test report under
   `build/reports/unified-tests/`.

## Test-bundle anatomy

`TestBundleBuilder` uses bnd's `Builder` API directly to assemble a bundle containing:

- Every compiled `*.class` file in the module's `build/classes/java/test`.
- Imports for JUnit 4 and Hamcrest from the Liferay test runtime provided by
  `com.liferay.portal.test` rather than embedding those libraries as
  `Private-Package`. This keeps `org.junit.rules.TestRule` and related types
  consistent with Liferay's own `LiferayIntegrationTestRule` and avoids
  duplicate copies inside the generated test bundle.
- The `fi.okm.jod.ohjaaja.cms.testrunner.runtime` package, which is the
  `InContainerTestExecutor` invoked by the OSGi servlet.
- The `fi.okm.jod.ohjaaja.cms.testrunner.client` packages embedded as
  `Private-Package` so the `@RunWith(JodInContainerRunner.class)` annotation
  has a resolvable target inside the container (these classes are never
  invoked there. They just need to be loadable for annotation reflection).

The bundle has no `Bundle-Activator`. It is a passive code library that the
OSGi runner bundle loads classes from on demand.
