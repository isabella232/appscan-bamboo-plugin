/**
 * (c) Copyright HCL Technologies Ltd. 2020.
 * LICENSE: Apache License, Version 2.0 https://www.apache.org/licenses/LICENSE-2.0
 */

package com.hcl.appscan.bamboo.plugin.impl;

import com.atlassian.bamboo.build.artifact.ArtifactManager;
import com.atlassian.bamboo.credentials.CredentialsData;
import com.atlassian.bamboo.plan.artifact.ArtifactDefinitionContext;
import com.atlassian.bamboo.plan.artifact.ArtifactDefinitionContextImpl;
import com.atlassian.bamboo.plan.artifact.ArtifactPublishingResult;
import com.atlassian.bamboo.task.TaskContext;
import com.atlassian.bamboo.task.TaskException;
import com.atlassian.bamboo.variable.VariableContext;
import com.atlassian.bamboo.variable.VariableDefinitionContext;
import com.hcl.appscan.sdk.CoreConstants;
import com.hcl.appscan.sdk.results.IResultsProvider;
import com.hcl.appscan.sdk.scanners.sast.SASTConstants;
import com.hcl.appscan.sdk.utils.SystemUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

public abstract class AbstractASoCScanner implements IScanner {
	protected LogHelper logger;
	protected ArtifactManager artifactManager;
	protected ResultsRetriever resultsRetriever;
	protected IResultsProvider provider;

	protected CredentialsData credential;
	protected File workingDir;
	protected String utilPath;

	protected long high;
	protected long medium;
	protected long low;

	public AbstractASoCScanner(LogHelper logger, ArtifactManager artifactManager) {
		this.logger = logger;
		this.artifactManager = artifactManager;
	}

	@Override
	public void publishArtifact(TaskContext taskContext, String name, File directory, String pattern) {
		logger.info("publish.artifact", name); //$NON-NLS-1$

		ArtifactDefinitionContext artifact = new ArtifactDefinitionContextImpl(name, true, null);
		artifact.setCopyPattern(pattern);

		ArtifactPublishingResult result = artifactManager.publish(
				taskContext.getBuildLogger(),
				taskContext.getBuildContext().getPlanResultKey(),
				directory,
				artifact,
				new Hashtable<String, String>(),
				1);

		taskContext.getBuildContext().getArtifactContext().addPublishingResult(result);
	}

	@Override
	public void setCredential(CredentialsData credentials) {
		this.credential = credentials;
	}

	protected Map<String, String> getScanProperties(TaskContext taskContext) throws TaskException {
		Map<String, String> properties = new HashMap<String, String>();
		addEntryMap(properties, CoreConstants.SCANNER_TYPE, getScannerType());
		addEntryMap(properties, CoreConstants.APP_ID, taskContext.getConfigurationMap().get(CFG_APP_ID));
		addEntryMap(properties, CoreConstants.SCAN_NAME, taskContext.getBuildContext().getPlanName() + "_" + SystemUtil.getTimeStamp()); //$NON-NLS-1$
		addEntryMap(properties, CoreConstants.EMAIL_NOTIFICATION, Boolean.toString(false));
		addEntryMap(properties, SASTConstants.APPSCAN_IRGEN_CLIENT, "Bamboo");
		addEntryMap(properties, SASTConstants.APPSCAN_CLIENT_VERSION, System.getProperty(SDK_VERSION_KEY, ""));
		addEntryMap(properties, SASTConstants.IRGEN_CLIENT_PLUGIN_VERSION, "1");
		addEntryMap(properties, "ClientType", "Bamboo-" + SystemUtil.getOS() + "-" + "1");
		return properties;
	}

	protected void addEntryMap(Map<String, String> m, String key, Object value) {
		if (value != null) m.put(key, value.toString().trim());
	}

	@Override
	public void waitAndDownloadResult(TaskContext taskContext) throws TaskException, InterruptedException {
		waitForReady(taskContext);
		downloadResult(taskContext);
	}

	@Override
	public void setWorkingDir(File workingDir) {
		this.workingDir = workingDir;
	}

	@Override
	public long getHighCount() {
		return high;
	}

	@Override
	public long getMediumCount() {
		return medium;
	}

	@Override
	public long getLowCount() {
		return low;
	}

	@Override
	public void waitForReady(TaskContext taskContext) throws TaskException, InterruptedException {
		setRetryInterval(taskContext);

		resultsRetriever.waitForResults();
		if (resultsRetriever.hasFailed()) {
			throw new TaskException(resultsRetriever.getMessage());
		} else {
			low = provider.getLowCount();
			medium = provider.getMediumCount();
			high = provider.getHighCount();
		}
	}

	protected void setRetryInterval(TaskContext taskContext) {
		VariableContext variables = taskContext.getBuildContext().getVariableContext();
		VariableDefinitionContext variable = variables.getEffectiveVariables().get(APPSCAN_INTERVAL);
		String value = variable == null ? null : variable.getValue();
		int retryInterval = DEFAULT_RETRY_INTERVAL;
		if (value != null && !value.trim().isEmpty()) {
			try {
				retryInterval = Math.max(Integer.parseInt(value.trim()), MIN_RETRY_INTERVAL);
			} catch (NumberFormatException e) {
			}
		}
		resultsRetriever.setRetryInterval(retryInterval);
	}

	@Override
	public void downloadResult(TaskContext taskContext) throws TaskException {
		String reportName = taskContext.getBuildContext().getPlanName().replaceAll(" ", "") + REPORT_SUFFIX + "." + provider.getResultsFormat().toLowerCase();
		File file = new File(workingDir, reportName);
		if (!file.isFile())
			provider.getResultsFile(file, null);

		publishArtifact(taskContext, logger.getText("result.artifact"), workingDir, reportName);
	}
}
