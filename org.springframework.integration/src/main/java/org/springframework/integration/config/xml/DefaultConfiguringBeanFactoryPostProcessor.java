/*
 * Copyright 2002-2009 the original author or authors.
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

package org.springframework.integration.config.xml;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.task.TaskExecutor;
import org.springframework.integration.channel.NullChannel;
import org.springframework.integration.config.xml.IntegrationNamespaceUtils;
import org.springframework.integration.context.IntegrationContextUtils;

/**
 * A {@link BeanFactoryPostProcessor} implementation that provides default
 * beans for the error handling and task scheduling if those beans have not
 * already been explicitly defined within the registry. It also registers a
 * single null channel with the bean name "nullChannel".
 * 
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 */
class DefaultConfiguringBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

	private Log logger = LogFactory.getLog(this.getClass());


	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		if (beanFactory instanceof BeanDefinitionRegistry) {
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
			this.registerNullChannel(registry);
			this.registerErrorChannelIfNecessary(registry);
			this.registerTaskSchedulerIfNecessary(registry);
		}
		else if (logger.isWarnEnabled()) {
			logger.warn("BeanFactory is not a BeanDefinitionRegistry. The default '"
					+ IntegrationContextUtils.ERROR_CHANNEL_BEAN_NAME + "' and '"
					+ IntegrationContextUtils.TASK_SCHEDULER_BEAN_NAME + "' cannot be configured.");
		}
	}

	/**
	 * Register a null channel in the given BeanDefinitionRegistry. The bean name is
	 * defined by the constant {@link IntegrationContextUtils#NULL_CHANNEL_BEAN_NAME}.
	 */
	private void registerNullChannel(BeanDefinitionRegistry registry) {
		if (registry.isBeanNameInUse(IntegrationContextUtils.NULL_CHANNEL_BEAN_NAME)) {
			BeanDefinition bDef = registry.getBeanDefinition(IntegrationContextUtils.NULL_CHANNEL_BEAN_NAME);
			if (bDef.getBeanClassName().equals(NullChannel.class.getName())){
				return;
			} else {
				throw new IllegalStateException("The bean name '" +
						IntegrationContextUtils.NULL_CHANNEL_BEAN_NAME + "' is reserved.");
			}
		} else {
			RootBeanDefinition nullChannelDef = new RootBeanDefinition();
			nullChannelDef.setBeanClassName(IntegrationNamespaceUtils.BASE_PACKAGE + ".channel.NullChannel");
			BeanDefinitionHolder nullChannelHolder = new BeanDefinitionHolder(
					nullChannelDef, IntegrationContextUtils.NULL_CHANNEL_BEAN_NAME);
			BeanDefinitionReaderUtils.registerBeanDefinition(nullChannelHolder, registry);
		}
	}

	/**
	 * Register an error channel in the given BeanDefinitionRegistry if not yet present.
	 * The bean name for which this is checking is defined by the constant
	 * {@link IntegrationContextUtils#ERROR_CHANNEL_BEAN_NAME}.
	 */
	private void registerErrorChannelIfNecessary(BeanDefinitionRegistry registry) {
		if (!registry.isBeanNameInUse(IntegrationContextUtils.ERROR_CHANNEL_BEAN_NAME)) {
			if (logger.isInfoEnabled()) {
				logger.info("No bean named '" + IntegrationContextUtils.ERROR_CHANNEL_BEAN_NAME +
						"' has been explicitly defined. Therefore, a default PublishSubscribeChannel will be created.");
			}
			RootBeanDefinition errorChannelDef = new RootBeanDefinition();
			errorChannelDef.setBeanClassName(IntegrationNamespaceUtils.BASE_PACKAGE + ".channel.PublishSubscribeChannel");
			BeanDefinitionHolder errorChannelHolder = new BeanDefinitionHolder(
					errorChannelDef, IntegrationContextUtils.ERROR_CHANNEL_BEAN_NAME);
			BeanDefinitionReaderUtils.registerBeanDefinition(errorChannelHolder, registry);
			BeanDefinitionBuilder loggingHandlerBuilder = BeanDefinitionBuilder.genericBeanDefinition(
					IntegrationNamespaceUtils.BASE_PACKAGE + ".handler.LoggingHandler");
			loggingHandlerBuilder.addConstructorArgValue("ERROR");
			String loggingHandlerBeanName = BeanDefinitionReaderUtils.registerWithGeneratedName(
					loggingHandlerBuilder.getBeanDefinition(), registry);
			BeanDefinitionBuilder loggingEndpointBuilder = BeanDefinitionBuilder.genericBeanDefinition(
					IntegrationNamespaceUtils.BASE_PACKAGE + ".endpoint.EventDrivenConsumer");
			loggingEndpointBuilder.addConstructorArgReference(IntegrationContextUtils.ERROR_CHANNEL_BEAN_NAME);
			loggingEndpointBuilder.addConstructorArgReference(loggingHandlerBeanName);
			BeanDefinitionReaderUtils.registerWithGeneratedName(loggingEndpointBuilder.getBeanDefinition(), registry);
		}
	}

	/**
	 * Register a TaskScheduler in the given BeanDefinitionRegistry if not yet present.
	 * The bean name for which this is checking is defined by the constant
	 * {@link IntegrationContextUtils#TASK_SCHEDULER_BEAN_NAME}.
	 */
	private void registerTaskSchedulerIfNecessary(BeanDefinitionRegistry registry) {
		if (!registry.isBeanNameInUse(IntegrationContextUtils.TASK_SCHEDULER_BEAN_NAME)) {
			if (logger.isInfoEnabled()) {
				logger.info("No bean named '" + IntegrationContextUtils.TASK_SCHEDULER_BEAN_NAME +
						"' has been explicitly defined. Therefore, a default SimpleTaskScheduler will be created.");
			}
			TaskExecutor taskExecutor = IntegrationContextUtils.createThreadPoolTaskExecutor(2, 100, 0, "task-scheduler-");
			BeanDefinitionBuilder schedulerBuilder = BeanDefinitionBuilder.genericBeanDefinition(
					IntegrationNamespaceUtils.BASE_PACKAGE + ".scheduling.SimpleTaskScheduler");
			schedulerBuilder.addConstructorArgValue(taskExecutor);
			BeanDefinitionBuilder errorHandlerBuilder = BeanDefinitionBuilder.genericBeanDefinition(
					IntegrationNamespaceUtils.BASE_PACKAGE + ".channel.MessagePublishingErrorHandler");
			errorHandlerBuilder.addPropertyReference("defaultErrorChannel", IntegrationContextUtils.ERROR_CHANNEL_BEAN_NAME);
			String errorHandlerBeanName = BeanDefinitionReaderUtils.registerWithGeneratedName(
					errorHandlerBuilder.getBeanDefinition(), registry);
			schedulerBuilder.addPropertyReference("errorHandler", errorHandlerBeanName);
			BeanDefinitionHolder schedulerHolder = new BeanDefinitionHolder(
					schedulerBuilder.getBeanDefinition(), IntegrationContextUtils.TASK_SCHEDULER_BEAN_NAME);
			BeanDefinitionReaderUtils.registerBeanDefinition(schedulerHolder, registry);
		}
	}

}