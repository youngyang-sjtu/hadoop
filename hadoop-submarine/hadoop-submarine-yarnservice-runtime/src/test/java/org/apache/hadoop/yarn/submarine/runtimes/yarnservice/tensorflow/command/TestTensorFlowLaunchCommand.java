/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.submarine.runtimes.yarnservice.tensorflow.command;

import com.google.common.collect.ImmutableList;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.service.api.records.Component;
import org.apache.hadoop.yarn.submarine.client.cli.param.RunJobParameters;
import org.apache.hadoop.yarn.submarine.common.MockClientContext;
import org.apache.hadoop.yarn.submarine.common.api.TaskType;
import org.apache.hadoop.yarn.submarine.runtimes.yarnservice.FileSystemOperations;
import org.apache.hadoop.yarn.submarine.runtimes.yarnservice.HadoopEnvironmentSetup;
import org.apache.hadoop.yarn.submarine.runtimes.yarnservice.command.AbstractLaunchCommandTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.apache.hadoop.yarn.submarine.runtimes.yarnservice.HadoopEnvironmentSetup.DOCKER_HADOOP_HDFS_HOME;
import static org.apache.hadoop.yarn.submarine.runtimes.yarnservice.HadoopEnvironmentSetup.DOCKER_JAVA_HOME;

/**
 * This class is to test the implementors of {@link TensorFlowLaunchCommand}.
 */
@RunWith(Parameterized.class)
public class TestTensorFlowLaunchCommand
    extends AbstractLaunchCommandTestHelper {
  private TaskType taskType;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    Collection<Object[]> params = new ArrayList<>();
    params.add(new Object[]{TaskType.WORKER });
    params.add(new Object[]{TaskType.PS });
    return params;
  }

  public TestTensorFlowLaunchCommand(TaskType taskType) {
    this.taskType = taskType;
  }


  private void assertScriptContainsLaunchCommand(List<String> fileContents,
      RunJobParameters params) {
    String launchCommand = null;
    if (taskType == TaskType.WORKER) {
      launchCommand = params.getWorkerLaunchCmd();
    } else if (taskType == TaskType.PS) {
      launchCommand = params.getPSLaunchCmd();
    }
    assertScriptContainsLine(fileContents, launchCommand);
  }

  private void setLaunchCommandToParams(RunJobParameters params) {
    if (taskType == TaskType.WORKER) {
      params.setWorkerLaunchCmd("testWorkerLaunchCommand");
    } else if (taskType == TaskType.PS) {
      params.setPSLaunchCmd("testPsLaunchCommand");
    }
  }

  private void setLaunchCommandToParams(RunJobParameters params, String value) {
    if (taskType == TaskType.WORKER) {
      params.setWorkerLaunchCmd(value);
    } else if (taskType == TaskType.PS) {
      params.setPSLaunchCmd(value);
    }
  }

  private void assertTypeInJson(List<String> fileContents) {
    String expectedType = null;
    if (taskType == TaskType.WORKER) {
      expectedType = "worker";
    } else if (taskType == TaskType.PS) {
      expectedType = "ps";
    }
    assertScriptContainsLineWithRegex(fileContents, String.format(".*type.*:" +
        ".*%s.*", expectedType));
  }

  private TensorFlowLaunchCommand createTensorFlowLaunchCommandObject(
      HadoopEnvironmentSetup hadoopEnvSetup, Configuration yarnConfig,
      Component component, RunJobParameters params) throws IOException {
    if (taskType == TaskType.WORKER) {
      return new TensorFlowWorkerLaunchCommand(hadoopEnvSetup, taskType,
          component,
          params, yarnConfig);
    } else if (taskType == TaskType.PS) {
      return new TensorFlowPsLaunchCommand(hadoopEnvSetup, taskType, component,
          params, yarnConfig);
    }
    throw new IllegalStateException("Unknown tasktype!");
  }

  @Test
  public void testHdfsRelatedEnvironmentIsUndefined() throws IOException {
    RunJobParameters params = new RunJobParameters();
    params.setInputPath("hdfs://bla");
    params.setName("testJobname");
    setLaunchCommandToParams(params);

    testHdfsRelatedEnvironmentIsUndefined(taskType, params);
  }

  @Test
  public void testHdfsRelatedEnvironmentIsDefined() throws IOException {
    RunJobParameters params = new RunJobParameters();
    params.setName("testName");
    params.setInputPath("hdfs://bla");
    params.setEnvars(ImmutableList.of(
        DOCKER_HADOOP_HDFS_HOME + "=" + "testHdfsHome",
        DOCKER_JAVA_HOME + "=" + "testJavaHome"));
    setLaunchCommandToParams(params);

    List<String> fileContents =
        testHdfsRelatedEnvironmentIsDefined(taskType,
            params);
    assertScriptContainsLaunchCommand(fileContents, params);
    assertScriptDoesNotContainLine(fileContents, "export TF_CONFIG=");
  }

  @Test
  public void testLaunchCommandIsNull() throws IOException {
    MockClientContext mockClientContext = new MockClientContext();
    FileSystemOperations fsOperations =
        new FileSystemOperations(mockClientContext);
    HadoopEnvironmentSetup hadoopEnvSetup =
        new HadoopEnvironmentSetup(mockClientContext, fsOperations);
    Configuration yarnConfig = new Configuration();

    Component component = new Component();
    RunJobParameters params = new RunJobParameters();
    params.setName("testName");
    setLaunchCommandToParams(params, null);

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("LaunchCommand must not be null or empty");
    TensorFlowLaunchCommand launchCommand =
        createTensorFlowLaunchCommandObject(hadoopEnvSetup, yarnConfig,
            component,
        params);
    launchCommand.generateLaunchScript();
  }

  @Test
  public void testLaunchCommandIsEmpty() throws IOException {
    MockClientContext mockClientContext = new MockClientContext();
    FileSystemOperations fsOperations =
        new FileSystemOperations(mockClientContext);
    HadoopEnvironmentSetup hadoopEnvSetup =
        new HadoopEnvironmentSetup(mockClientContext, fsOperations);
    Configuration yarnConfig = new Configuration();

    Component component = new Component();
    RunJobParameters params = new RunJobParameters();
    params.setName("testName");
    setLaunchCommandToParams(params, "");

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("LaunchCommand must not be null or empty");
    TensorFlowLaunchCommand launchCommand =
        createTensorFlowLaunchCommandObject(hadoopEnvSetup, yarnConfig,
            component, params);
    launchCommand.generateLaunchScript();
  }

  @Test
  public void testDistributedTrainingMissingTaskType() throws IOException {
    overrideTaskType(null);

    RunJobParameters params = new RunJobParameters();
    params.setDistributed(true);
    params.setName("testName");
    params.setInputPath("hdfs://bla");
    params.setEnvars(ImmutableList.of(
        DOCKER_HADOOP_HDFS_HOME + "=" + "testHdfsHome",
        DOCKER_JAVA_HOME + "=" + "testJavaHome"));
    setLaunchCommandToParams(params);

    expectedException.expect(NullPointerException.class);
    expectedException.expectMessage("TaskType must not be null");
    testHdfsRelatedEnvironmentIsDefined(taskType, params);
  }

  @Test
  public void testDistributedTrainingNumberOfWorkersAndPsIsZero()
      throws IOException {
    RunJobParameters params = new RunJobParameters();
    params.setDistributed(true);
    params.setNumWorkers(0);
    params.setNumPS(0);
    params.setName("testName");
    params.setInputPath("hdfs://bla");
    params.setEnvars(ImmutableList.of(
        DOCKER_HADOOP_HDFS_HOME + "=" + "testHdfsHome",
        DOCKER_JAVA_HOME + "=" + "testJavaHome"));
    setLaunchCommandToParams(params);

    List<String> fileContents =
        testHdfsRelatedEnvironmentIsDefined(taskType, params);

    assertScriptDoesNotContainLine(fileContents, "export TF_CONFIG=");
    assertScriptContainsLineWithRegex(fileContents, ".*worker.*:\\[\\].*");
    assertScriptContainsLineWithRegex(fileContents, ".*ps.*:\\[\\].*");
    assertTypeInJson(fileContents);
  }

  @Test
  public void testDistributedTrainingNumberOfWorkersAndPsIsNonZero()
      throws IOException {
    RunJobParameters params = new RunJobParameters();
    params.setDistributed(true);
    params.setNumWorkers(3);
    params.setNumPS(2);
    params.setName("testName");
    params.setInputPath("hdfs://bla");
    params.setEnvars(ImmutableList.of(
        DOCKER_HADOOP_HDFS_HOME + "=" + "testHdfsHome",
        DOCKER_JAVA_HOME + "=" + "testJavaHome"));
    setLaunchCommandToParams(params);

    List<String> fileContents =
        testHdfsRelatedEnvironmentIsDefined(taskType, params);

    //assert we have multiple PS and workers
    assertScriptDoesNotContainLine(fileContents, "export TF_CONFIG=");
    assertScriptContainsLineWithRegex(fileContents, ".*worker.*:\\[.*,.*\\].*");
    assertScriptContainsLineWithRegex(fileContents, ".*ps.*:\\[.*,.*\\].*");
    assertTypeInJson(fileContents);
  }


}