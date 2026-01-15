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

This is a Liferay Workspace – A structured development environment for managing Liferay projects.

### Prerequisites

Before setting up the local development environment, ensure that you have the following installed:

- Java 21
- Gradle
- Docker
- Docker Compose

### Setting Up the Local Development Environment

To build the Docker image for the application, run the following command:

```sh
./gradlew clean buildDockerImage
```

Once the Docker image is built, start the local test environment using:

```sh
docker-compose up
```

### Deploying Changes to Running Modules

When the test environment is running, you can deploy changes to modules with the following command:

```sh
./gradlew deploy
```

This command will apply updates to the running environment without requiring a restart.

## Testing

### Running All Integration Tests

Run all integration tests in a Docker container with a single command:

```sh
./gradlew testWithDockerContainer
```

This will:
- Build and start a Liferay test container
- Automatically discover and run all test modules (modules ending with `-test`)
- Generate a unified HTML test report at `build/reports/unified-tests/index.html`
- Clean up the container after tests complete

### Generate Unified Test Report

After running tests, generate or view the unified report:

```sh
./gradlew unifiedTestReport
```

### Adding New Test Modules

1. Create a new module in `modules/` directory with name ending in `-test`
   - Example: `modules/jod-ohjaaja-cms-myfeature-test/`

2. That's it! The test will be automatically discovered and included.

For detailed testing documentation, see [test/ARQUILLIAN_README.md](test/ARQUILLIAN_README.md).


