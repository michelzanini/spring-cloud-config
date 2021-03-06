/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.bootstrap.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.PropertySourceLocator;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * @author Dave Syer
 *
 */
public class BootstrapConfigurationTests {

	private ConfigurableApplicationContext context;
	
	@Rule
	public ExpectedException expected = ExpectedException.none();

	@After
	public void close() {
		// Expected.* is bound to the PropertySourceConfiguration below
		System.clearProperty("expected.name");
		System.clearProperty("expected.fail");
		// Used to test system properties override
		System.clearProperty("bootstrap.foo");
		PropertySourceConfiguration.MAP.clear();
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void pickupExternalBootstrapProperties() {
		String externalPropertiesPath = getExternalProperties();

		context = new SpringApplicationBuilder().web(false)
				.sources(BareConfiguration.class)
				.properties("spring.cloud.bootstrap.location:" + externalPropertiesPath)
				.run();
		assertEquals("externalPropertiesInfoName",
				context.getEnvironment().getProperty("info.name"));
		assertTrue(context.getEnvironment().getPropertySources().contains("bootstrap"));
		assertNotNull(context.getBean(ConfigClientProperties.class));
	}

	/**
	 * Running the test from maven will start from a different directory then starting it
	 * from intellij
	 *
	 * @return
	 */
	private String getExternalProperties() {
		String externalPropertiesPath = "";
		File externalProperties = new File(
				"src/test/resources/external-properties/bootstrap.properties");
		if (externalProperties.exists()) {
			externalPropertiesPath = externalProperties.getAbsolutePath();
		}
		else {
			externalProperties = new File(
					"spring-cloud-config-client/src/test/resources/external-properties/bootstrap.properties");
			externalPropertiesPath = externalProperties.getAbsolutePath();
		}
		return externalPropertiesPath;
	}

	@Test
	public void picksUpAdditionalPropertySource() {
		PropertySourceConfiguration.MAP.put("bootstrap.foo", "bar");
		System.setProperty("expected.name", "bootstrap");
		context = new SpringApplicationBuilder().web(false)
				.sources(BareConfiguration.class).run();
		assertEquals("bar", context.getEnvironment().getProperty("bootstrap.foo"));
		assertTrue(context.getEnvironment().getPropertySources().contains("bootstrap"));
		assertNotNull(context.getBean(ConfigClientProperties.class));
	}

	@Test
	public void failsOnPropertySource() {
		System.setProperty("expected.fail", "true");
		expected.expectMessage("Planned");
		context = new SpringApplicationBuilder().web(false)
				.sources(BareConfiguration.class).run();
	}

	@Test
	public void overrideSystemPropertySourceByDefault() {
		PropertySourceConfiguration.MAP.put("bootstrap.foo", "bar");
		System.setProperty("bootstrap.foo", "system");
		context = new SpringApplicationBuilder().web(false)
				.sources(BareConfiguration.class).run();
		assertEquals("bar", context.getEnvironment().getProperty("bootstrap.foo"));
	}

	@Test
	public void systemPropertyOverrideFalse() {
		PropertySourceConfiguration.MAP.put("bootstrap.foo", "bar");
		PropertySourceConfiguration.MAP.put(
				"spring.cloud.config.overrideSystemProperties", "false");
		System.setProperty("bootstrap.foo", "system");
		context = new SpringApplicationBuilder().web(false)
				.sources(BareConfiguration.class).run();
		assertEquals("system", context.getEnvironment().getProperty("bootstrap.foo"));
	}

	@Test
	public void systemPropertyOverrideWhenOverrideDisallowed() {
		PropertySourceConfiguration.MAP.put("bootstrap.foo", "bar");
		PropertySourceConfiguration.MAP.put(
				"spring.cloud.config.overrideSystemProperties", "false");
		// If spring.cloud.config.allowOverride=false is in the remote property sources
		// with sufficiently high priority it always wins. Admins can enforce it by adding
		// their own remote property source.
		PropertySourceConfiguration.MAP.put("spring.cloud.config.allowOverride", "false");
		System.setProperty("bootstrap.foo", "system");
		context = new SpringApplicationBuilder().web(false)
				.sources(BareConfiguration.class).run();
		assertEquals("bar", context.getEnvironment().getProperty("bootstrap.foo"));
	}

	@Test
	public void applicationNameInBootstrapAndMain() {
		System.setProperty("expected.name", "main");
		context = new SpringApplicationBuilder()
				.web(false)
				.properties("spring.cloud.bootstrap.name:other",
						"spring.config.name:plain").sources(BareConfiguration.class)
				.run();
		assertEquals("app",
				context.getEnvironment().getProperty("spring.application.name"));
		// The parent is called "main" because spring.application.name is specified in
		// other.properties (the bootstrap properties)
		assertEquals(
				"main",
				context.getParent().getEnvironment()
						.getProperty("spring.application.name"));
		// The bootstrap context has a different "bootstrap" property source
		assertNotSame(context.getEnvironment().getPropertySources().get("bootstrap"),
				((ConfigurableEnvironment) context.getParent().getEnvironment())
						.getPropertySources().get("bootstrap"));
		assertEquals("app", context.getId());
	}

	@Test
	public void applicationNameNotInBootstrap() {
		System.setProperty("expected.name", "main");
		context = new SpringApplicationBuilder()
				.web(false)
				.properties("spring.cloud.bootstrap.name:application",
						"spring.config.name:other").sources(BareConfiguration.class)
				.run();
		assertEquals("main",
				context.getEnvironment().getProperty("spring.application.name"));
		// The parent is called "application" because spring.application.name is not
		// defined in the bootstrap properties
		assertEquals(
				"application",
				context.getParent().getEnvironment()
						.getProperty("spring.application.name"));
	}

	@Test
	public void applicationNameOnlyInBootstrap() {
		System.setProperty("expected.name", "main");
		context = new SpringApplicationBuilder().web(false)
				.properties("spring.cloud.bootstrap.name:other")
				.sources(BareConfiguration.class).run();
		// The main context is called "main" because spring.application.name is specified
		// in other.properties (and not in the main config file)
		assertEquals("main",
				context.getEnvironment().getProperty("spring.application.name"));
		// The parent is called "main" because spring.application.name is specified in
		// other.properties (the bootstrap properties this time)
		assertEquals(
				"main",
				context.getParent().getEnvironment()
						.getProperty("spring.application.name"));
		assertEquals("main", context.getId());
	}

	@Test
	public void environmentEnrichedOnceWhenSharedWithChildContext() {
		PropertySourceConfiguration.MAP.put("bootstrap.foo", "bar");
		context = new SpringApplicationBuilder().sources(BareConfiguration.class)
				.environment(new StandardEnvironment()).child(BareConfiguration.class)
				.web(false).run();
		assertEquals("bar", context.getEnvironment().getProperty("bootstrap.foo"));
		assertEquals(context.getEnvironment(), context.getParent().getEnvironment());
		MutablePropertySources sources = context.getEnvironment().getPropertySources();
		PropertySource<?> bootstrap = sources.get("bootstrap");
		assertNotNull(bootstrap);
		assertEquals(0, sources.precedenceOf(bootstrap));
	}

	@Test
	public void environmentEnrichedInParentContext() {
		PropertySourceConfiguration.MAP.put("bootstrap.foo", "bar");
		context = new SpringApplicationBuilder().sources(BareConfiguration.class)
				.child(BareConfiguration.class).web(false).run();
		assertEquals("bar", context.getEnvironment().getProperty("bootstrap.foo"));
		assertNotSame(context.getEnvironment(), context.getParent().getEnvironment());
		assertTrue(context.getEnvironment().getPropertySources().contains("bootstrap"));
		assertTrue(((ConfigurableEnvironment) context.getParent().getEnvironment())
				.getPropertySources().contains("bootstrap"));
	}

	@Test
	public void differentProfileInChild() {
		PropertySourceConfiguration.MAP.put("bootstrap.foo", "bar");
		// Profiles are always merged with the child
		ConfigurableApplicationContext parent = new SpringApplicationBuilder()
				.sources(BareConfiguration.class).profiles("parent").web(false).run();
		context = new SpringApplicationBuilder(BareConfiguration.class).profiles("child")
				.parent(parent).web(false).run();
		assertNotSame(context.getEnvironment(), context.getParent().getEnvironment());
		// The ApplicationContext merges profiles (profiles and property sources), see
		// AbstractEnvironment.merge()
		assertTrue(this.context.getEnvironment().acceptsProfiles("child", "parent"));
		// But the parent is not a child
		assertFalse(this.context.getParent().getEnvironment().acceptsProfiles("child"));
		assertTrue(this.context.getParent().getEnvironment().acceptsProfiles("parent"));
		assertTrue(((ConfigurableEnvironment) context.getParent().getEnvironment())
				.getPropertySources().contains("bootstrap"));
		assertEquals("bar", context.getEnvironment().getProperty("bootstrap.foo"));
		// The "bootstrap" property source is not shared now, but it has the same
		// properties in it because they are pulled from the PropertySourceConfiguration
		// below
		assertEquals("bar",
				context.getParent().getEnvironment().getProperty("bootstrap.foo"));
		// The parent property source is there in the child because they are both in the
		// "parent" profile (by virtue of the merge in AbstractEnvironment)
		assertEquals("parent", context.getEnvironment().getProperty("info.name"));
	}

	@Configuration
	@EnableConfigurationProperties(ConfigClientProperties.class)
	protected static class BareConfiguration {
	}

	@Configuration
	@ConfigurationProperties("expected")
	// This is added to bootstrap context as a source in bootstrap.properties
	protected static class PropertySourceConfiguration implements PropertySourceLocator {

		public static Map<String, Object> MAP = new HashMap<String, Object>(
				Collections.<String, Object> singletonMap("bootstrap.foo", "bar"));

		private String name;
		
		private boolean fail = false;

		@Override
		public PropertySource<?> locate(Environment environment) {
			if (name != null) {
				assertEquals(name, environment.getProperty("spring.application.name"));
			}
			if (fail) {
				throw new RuntimeException("Planned");
			}
			return new MapPropertySource("testBootstrap", MAP);
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public boolean isFail() {
			return fail;
		}

		public void setFail(boolean fail) {
			this.fail = fail;
		}
	}

}
