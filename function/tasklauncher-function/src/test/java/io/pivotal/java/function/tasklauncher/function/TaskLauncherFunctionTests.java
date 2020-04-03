/*
 * Copyright 2019-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.pivotal.java.function.tasklauncher.function;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.dataflow.rest.client.DataFlowOperations;
import org.springframework.cloud.dataflow.rest.client.TaskOperations;
import org.springframework.cloud.dataflow.rest.resource.CurrentTaskExecutionsResource;
import org.springframework.cloud.dataflow.rest.resource.LauncherResource;
import org.springframework.context.Lifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.hateoas.PagedModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
public class TaskLauncherFunctionTests {

	@Autowired
	private TaskLauncherFunction taskLauncherFunction;

	@Autowired
	private TaskOperations taskOperations;

	@Test
	public void successfulLaunch() {
		LaunchRequest launchRequest = new LaunchRequest();
		launchRequest.setTaskName("someTask");
		setCurrentExecutionState(0);
		Optional<Long> taskId = taskLauncherFunction.apply(launchRequest);
		assertThat(taskId.isPresent()).isTrue();
		assertThat(taskId.get()).isEqualTo(1L);

		verify(taskOperations).launch("someTask",
				Collections.singletonMap(TaskLauncherFunction.TASK_PLATFORM_NAME, "default"),
				Collections.emptyList(),
				null);
	}

	@Test
	public void taskPlatformAtCapacity() {
		LaunchRequest launchRequest = new LaunchRequest();
		launchRequest.setTaskName("someTask");
		setCurrentExecutionState(3);
		Optional<Long> taskId = taskLauncherFunction.apply(launchRequest);
		assertThat(taskId.isPresent()).isFalse();
	}

	@Test
	public void platformMismatch() {
		LaunchRequest launchRequest = new LaunchRequest();
		launchRequest.setTaskName("someTask");
		launchRequest
				.setDeploymentProperties(Collections.singletonMap(TaskLauncherFunction.TASK_PLATFORM_NAME, "other"));
		setCurrentExecutionState(0);
		assertThrows(IllegalStateException.class, () -> taskLauncherFunction.apply(launchRequest));
	}

	private void setCurrentExecutionState(int runningExecutions) {
		CurrentTaskExecutionsResource currentTaskExecutionsResource = new CurrentTaskExecutionsResource();
		currentTaskExecutionsResource.setMaximumTaskExecutions(3);
		currentTaskExecutionsResource.setRunningExecutionCount(runningExecutions);
		currentTaskExecutionsResource.setName("default");
		when(taskOperations.currentTaskExecutions())
				.thenReturn(Collections.singletonList(currentTaskExecutionsResource));
		when(taskOperations.launch(anyString(), anyMap(), anyList(), isNull())).thenReturn(1L);
	}

	@Test
	public void noLaunchersConfigured() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner().withUserConfiguration(TestConfig.class);
		Exception exception = assertThrows(IllegalStateException.class, () -> contextRunner
				.withPropertyValues("spring.profiles.active=nolaunchers")
				.run(Lifecycle::isRunning));

		assertThat(exception.getCause()).isInstanceOf(BeanCreationException.class);
		assertThat(exception.getCause().getCause()).isInstanceOf(IllegalArgumentException.class);
		assertThat(exception.getCause().getCause().getMessage()).isEqualTo(
				"The Data Flow Server has no task platforms configured");
	}

	@Configuration
	@Import(TaskLauncherFunctionApplication.class)
	static class TestConfig {

		@Bean
		@Profile("default")
		TaskOperations taskOperations() {
			TaskOperations taskOperations = mock(TaskOperations.class);
			LauncherResource launcherResource = mock(LauncherResource.class);
			when(launcherResource.getName()).thenReturn("default");

			when(taskOperations.listPlatforms()).thenReturn(PagedModel.of(
					Collections.singletonList(launcherResource), (PagedModel.PageMetadata) null));
			return taskOperations;
		}

		@Bean
		@Profile("nolaunchers")
		TaskOperations taskOperationsNoLaunchers() {
			TaskOperations taskOperations = mock(TaskOperations.class);
			when(taskOperations.listPlatforms()).thenReturn(PagedModel.of(
					Collections.emptyList(), (PagedModel.PageMetadata) null));
			return taskOperations;
		}

		@Bean
		DataFlowOperations dataFlowOperations(TaskOperations taskOperations) {
			DataFlowOperations dataFlowOperations = mock(DataFlowOperations.class);
			when(dataFlowOperations.taskOperations()).thenReturn(taskOperations);
			return dataFlowOperations;
		}
	}

	@SpringBootApplication
	static class TestApplication {
	}
}
