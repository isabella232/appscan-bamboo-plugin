/**
 * (c) Copyright HCL Technologies Ltd. 2020.
 * LICENSE: Apache License, Version 2.0 https://www.apache.org/licenses/LICENSE-2.0
 */

package com.hcl.appscan.bamboo.plugin.impl;

import com.atlassian.bamboo.task.TaskContext;
import com.atlassian.bamboo.task.TaskException;

import java.io.File;

public interface IScanner extends ISASTConstants, IJSONConstants, IArtifactPublisher {
	public String SA_DIR = ".sa";

	public void setUsername(String username);

	public void setPassword(String password);

	public void setWorkingDir(File workingDir);

	public String getScannerType();

	public void scheduleScan(TaskContext taskContext) throws TaskException;

	public File initWorkingDir(TaskContext taskContext) throws TaskException;

	public void waitAndDownloadResult(TaskContext taskContext) throws TaskException, InterruptedException;

	public void waitForReady(TaskContext taskContext) throws TaskException, InterruptedException;

	public void downloadResult(TaskContext taskContext) throws TaskException;

	public long getHighCount();

	public long getMediumCount();

	public long getLowCount();
}
