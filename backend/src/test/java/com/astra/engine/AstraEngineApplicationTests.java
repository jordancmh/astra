package com.astra.engine;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AstraEngineApplicationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void postgisExtensionIsActive() {
		String version = jdbcTemplate.queryForObject("SELECT PostGIS_Version();", String.class);
		assertThat(version).isNotNull().isNotEmpty();
	}

}
