# Liferay Arquillian Integration Testing

This directory contains the Arquillian integration testing infrastructure for Liferay OSGi modules using Docker containers.

## 🎯 Overview

We use **Liferay's Arquillian Extension JUnit Bridge** to enable true in-container testing where:
- Tests run inside the Liferay OSGi container
- OSGi services can be accessed via BundleContext/ServiceReference
- Tests execute in the same runtime as production code

## Source Attribution

The following modules in this directory are **copied from Liferay source** with modifications:

- **arquillian-extension-junit-bridge** - Originally from [liferay-portal/modules/test/arquillian-extension-junit-bridge](https://github.com/liferay/liferay-portal/tree/master/modules/test/arquillian-extension-junit-bridge)
- **arquillian-extension-junit-bridge-connector** - Originally from [liferay-portal/modules/test/arquillian-extension-junit-bridge-connector](https://github.com/liferay/liferay-portal/tree/master/modules/test/arquillian-extension-junit-bridge-connector)

**License:** These modules are subject to the Liferay dual-license terms (LGPL-2.1-or-later OR proprietary EULA). See:
- [LIFERAY-LICENSE.md](LIFERAY-LICENSE.md) - Liferay's licensing information
- [LGPL-2.1-or-later.txt](LGPL-2.1-or-later.txt) - Full LGPL license text
- [Liferay's LICENSING.md](https://github.com/liferay/liferay-portal/blob/master/LICENSING.md) - Original source

**Modifications:** We modified these modules to support Docker networking and Gradle builds. See "Key Modifications to Liferay Components" section below for details.

## Architecture

### Components

1. **arquillian-extension-junit-bridge** - Modified Liferay component
   - Bridge between JUnit client and Liferay OSGi container
   - Handles test bundle deployment and execution
   - Manages communication via socket connection

2. **arquillian-extension-junit-bridge-connector** - Modified Liferay component
   - Server-side component running in Liferay
   - Receives test bundles, installs them, and executes tests
   - Returns results back to the client

3. **Test modules** (e.g., `jod-ohjaaja-cms-tags-test`)
   - Contain integration tests for specific modules
   - Tests are packaged as OSGi bundles and deployed to Liferay
   - Can inject and test any OSGi service

### Docker Test Image

The `Dockerfile.test` creates a Liferay image with:
- Arquillian connector pre-installed
- `com.liferay.portal.test` bundle for test utilities
- All application modules deployed

## Key Modifications to Liferay Components

To make Arquillian work with Docker containers and Gradle builds, we had to modify Liferay's components:

### 1. arquillian-extension-junit-bridge-connector

**Location:** `test/arquillian-extension-junit-bridge-connector/`

**Problem**: Original bound to `localhost`, unreachable from Docker host.

**Solution in `ArquillianConnector.java`:**
- Changed `InetAddress.getLoopbackAddress()` to bind to `0.0.0.0`
- Allows test client on host to connect to connector in Docker container

### 2. arquillian-extension-junit-bridge

**Location:** `test/arquillian-extension-junit-bridge/`

Multiple modifications for Docker networking, Gradle support, and OSGi compatibility.

**Changes in `SocketState.java`:**
- Bind server socket to `0.0.0.0` instead of loopback
- Enables Docker containers to establish socket connections with host

**Changes in `ClientState.java`:**
- Added `liferay.arquillian.report.host` system property
  - Overrides detected IP for Docker/WSL scenarios
  - Example: `-Dliferay.arquillian.report.host=172.17.0.1`
- Enhanced error reporting and debug output

**Changes in `BndBundleUtil.java`:**
- **Gradle support**: Detects `build/classes/java/test` (vs Maven's `test-classes/integration`)
- **JUnit handling**: Makes JUnit imports optional via `resolution:=optional`
  - Prevents OSGi resolution failures
- **Dynamic imports**: Added `DynamicImport-Package: org.junit.*,junit.*`
- **Private packages**: Embeds JUnit/Hamcrest via `Private-Package`
  - Ensures test framework available in OSGi runtime
- **Import exclusions**: Excludes JUnit/Hamcrest from Import-Package

## Writing Tests

### Example Test Class

```java
@RunWith(Arquillian.class)
public class TagsServiceTest {

    @ClassRule
    @Rule
    public static final AggregateTestRule aggregateTestRule =
        new LiferayIntegrationTestRule();

    private static TagsService tagsService;
    private static BundleContext bundleContext;
    private static ServiceReference<TagsService> serviceReference;

    @BeforeClass
    public static void setUpClass() {
        Bundle bundle = FrameworkUtil.getBundle(TagsServiceTest.class);
        bundleContext = bundle.getBundleContext();
        serviceReference = bundleContext.getServiceReference(TagsService.class);
        if (serviceReference != null) {
            tagsService = bundleContext.getService(serviceReference);
        }
    }

    @AfterClass
    public static void tearDownClass() {
        if (serviceReference != null && bundleContext != null) {
            bundleContext.ungetService(serviceReference);
        }
    }

    @Test
    public void shouldRetrieveTagsServiceFromOSGi() {
        assertNotNull("TagsService should be retrieved from OSGi", tagsService);
    }
}
```

### Key Points

- Use `@RunWith(Arquillian.class)` to enable Liferay's Arquillian like testing
- Retrieve OSGi services via `BundleContext.getServiceReference()` and `getService()`
- Use `@BeforeClass` to obtain service references before tests run
- Use `@AfterClass` to clean up service references
- Test methods are executed inside the Liferay container

**Note**: `@Inject` for OSGi services is not currently working in our setup. We use the standard OSGi service lookup pattern instead.

## Running Tests

### Run all tests against Docker container

```bash
./gradlew testWithDockerContainer
```

This will:
1. Build all modules
2. Build Docker test image with modules and Arquillian connector
3. Start Liferay container
4. Run all integration tests
5. Stop container

### Run specific test module

```bash
./gradlew :modules:jod-ohjaaja-cms-tags-test:test
```

Note: This requires a running Liferay instance with the Arquillian connector.

## Project Structure

```
test/
├── arquillian-extension-junit-bridge/          # Modified Liferay client
├── arquillian-extension-junit-bridge-connector/ # Modified Liferay server
├── ARQUILLIAN_README.md                        # This file
├── LIFERAY-LICENSE.md                          # Liferay licensing info
└── LGPL-2.1-or-later.txt                       # LGPL license text

modules/
├── jod-ohjaaja-cms-tags/                       # Module under test
└── jod-ohjaaja-cms-tags-test/                  # Integration tests
    └── src/test/java/
        └── TagsServiceTest.java
```

## Creating New Test Modules

1. Create test module with `-test` suffix (e.g., `my-module-test`)
2. Add dependency on module under test in `build.gradle`
3. Write test classes with `@RunWith(Arquillian.class)`
4. Tests will automatically run in `testWithDockerContainer` task

Example `build.gradle`:
```gradle
dependencies {
    compileOnly project(':modules:my-module')

    testCompileOnly group: "com.liferay.portal", name: "release.portal.api"
    testCompileOnly project(':test:arquillian-extension-junit-bridge')
}
```

## Benefits

✅ **True integration testing** - Tests run in real Liferay OSGi container
✅ **Service access** - Direct access to OSGi services via BundleContext
✅ **Isolated environment** - Each test run uses fresh Docker container
✅ **CI/CD ready** - Fully automated with Gradle
✅ **Fast feedback** - Test output captured in JUnit reports

## Troubleshooting

### Tests can't connect to Liferay

Check that the modified connector is deployed:
```bash
docker exec liferay-test-container ls -la /opt/liferay/osgi/modules/
```

### Service injection fails

Ensure the module under test is deployed to the Docker image. Check `Dockerfile.test` and the `copyModulesToDocker` task.

### Test bundle won't start

Check OSGi import requirements. The test bundle must import all packages it uses. Look at the `bnd.bnd` in your test module.
