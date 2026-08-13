# Workflow Metrics startup exception

During Liferay startup you may see, right after `License registered`:

```text
Exception in thread "...WorkflowMetricsPortalExecutor-1-dispatcher"
java.util.concurrent.RejectedExecutionException: ... rejected from ...[Shutting down, ...]
...
ERROR [liferay/background_task-*][BackgroundTaskMessageListener:*] Unable to execute background task
java.lang.NullPointerException: Cannot invoke "...BackgroundTaskExecutor.getIsolationLevel()" because "backgroundTaskExecutor" is null
```

## Cause

The Free Tier activation key is registered late in startup and cycles licensed modules while Workflow Metrics is still bootstrapping indexes. That rejects in-flight work and can leave a stale row in the `Lock_` table.

## Mitigation

JOD does not use Kaleo / Workflow Metrics, so automatic index bootstrap is disabled via Component Blacklist in
[configs/common/osgi/configs/com.liferay.portal.component.blacklist.internal.configuration.ComponentBlacklistConfiguration.config](../configs/common/osgi/configs/com.liferay.portal.component.blacklist.internal.configuration.ComponentBlacklistConfiguration.config):

```properties
blacklistComponentNames=["com.liferay.portal.workflow.metrics.internal.search.index.creation.instance.lifecycle.WorkflowMetricsIndexInitialRequestPortalInstanceLifecycleListener"]
```

The file name must match that DXP configuration PID (including the `.configuration` segment).

## Stale lock cleanup

If a stale lock remains from an earlier failed startup, remove it once via Control Panel → Server Administration → Script:

```groovy
import com.liferay.portal.kernel.service.LockLocalServiceUtil

LockLocalServiceUtil.unlock(
    "com.liferay.portal.kernel.backgroundtask.BackgroundTaskExecutor",
    "com.liferay.portal.workflow.metrics.internal.background.task.WorkflowMetricsReindexBackgroundTaskExecutor#<companyId>")
```

Replace `<companyId>` with the portal company ID from the lock key. Not needed when the blacklist is present before first startup on a given database.

## Enabling Workflow Metrics later

Remove that component from the blacklist (or delete the file), restart, and create/reindex indexes with the supported method for the DXP version in use.
