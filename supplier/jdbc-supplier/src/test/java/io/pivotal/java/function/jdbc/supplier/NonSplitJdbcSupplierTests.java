/*
 * Copyright 2020 the original author or authors.
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
package io.pivotal.java.function.jdbc.supplier;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Soby Chacko
 * @author Artem Bilan
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = {"jdbc.supplier.query=select id, name from test order by id", "jdbc.supplier.split=false"})
@DirtiesContext
public class NonSplitJdbcSupplierTests {

	@Autowired
	Supplier<Message<?>> jdbcSupplier;

	@Test
	void testExtraction() {
		final Message<?> message = jdbcSupplier.get();
		final List<Map<?, ?>> payload = (List<Map<?, ?>>) message.getPayload();
		assertThat(payload.size()).isEqualTo(3);
		Map<?, ?> map = payload.get(0);
		assertThat(map.get("ID")).isEqualTo(1L);
		assertThat(map.get("NAME")).isEqualTo("Bob");
		map = payload.get(1);
		assertThat(map.get("ID")).isEqualTo(2L);
		assertThat(map.get("NAME")).isEqualTo("Jane");
		map = payload.get(2);
		assertThat(map.get("ID")).isEqualTo(3L);
		assertThat(map.get("NAME")).isEqualTo("John");
	}

	@SpringBootApplication
	static class TestApplication {
	}
}
