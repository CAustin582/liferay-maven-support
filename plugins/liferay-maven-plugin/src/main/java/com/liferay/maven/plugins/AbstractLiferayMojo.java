/**
 * Copyright (c) 2000-2012 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.maven.plugins;

import com.liferay.portal.cache.MultiVMPoolImpl;
import com.liferay.portal.cache.memory.MemoryPortalCacheManager;
import com.liferay.portal.kernel.cache.MultiVMPoolUtil;
import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.util.HtmlImpl;
import com.liferay.portal.util.InitUtil;
import com.liferay.portal.util.PropsUtil;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.codehaus.plexus.archiver.manager.ArchiverManager;

/**
 * @author Mika Koivisto
 */
public abstract class AbstractLiferayMojo extends AbstractMojo {

	public void execute() throws MojoExecutionException {
		try {
			initPortal();

			initClassLoader();

			doExecute();
		}
		catch (Exception e) {
			if (e instanceof MojoExecutionException) {
				throw (MojoExecutionException)e;
			}
			else {
				throw new MojoExecutionException(e.getMessage(), e);
			}
		}
	}

	protected void initClassLoader() throws Exception {
		synchronized (AbstractLiferayMojo.class) {
			Class<?> clazz = getClass();

			URLClassLoader urlClassLoader =
				(URLClassLoader)clazz.getClassLoader();

			Method method = URLClassLoader.class.getDeclaredMethod(
				"addURL", URL.class);

			method.setAccessible(true);

			for (Object object : project.getCompileClasspathElements()) {
				String path = (String)object;

				File file = new File(path);

				URI uri = file.toURI();

				method.invoke(urlClassLoader, uri.toURL());
			}
		}
	}

	protected void initPortal() throws Exception {
		if (appServerLibPortalDir != null) {
			System.setProperty(
				"liferay.lib.portal.dir",
				appServerLibPortalDir.getAbsolutePath());
		}

		PropsUtil.reload();

		PropsUtil.set(
			PropsKeys.RESOURCE_ACTIONS_READ_PORTLET_RESOURCES,
			Boolean.FALSE.toString());

		PropsUtil.set(
			PropsKeys.SPRING_CONFIGS, "META-INF/service-builder-spring.xml");

		PropsUtil.set(
			PropsKeys.VELOCITY_ENGINE_LOGGER,
			"org.apache.velocity.runtime.log.NullLogSystem");

		InitUtil.initWithSpring();

		HtmlUtil htmlUtil = new HtmlUtil();

		htmlUtil.setHtml(new HtmlImpl());

		MemoryPortalCacheManager memoryPortalCacheManager =
			new MemoryPortalCacheManager();

		memoryPortalCacheManager.afterPropertiesSet();

		MultiVMPoolImpl multiVMPoolImpl = new MultiVMPoolImpl();

		multiVMPoolImpl.setPortalCacheManager(memoryPortalCacheManager);

		MultiVMPoolUtil multiVMPoolUtil = new MultiVMPoolUtil();

		multiVMPoolUtil.setMultiVMPool(multiVMPoolImpl);
	}

	protected Dependency createDependency(
		String groupId, String artifactId, String version, String classifier,
		String type) {

		Dependency dependency = new Dependency();

		dependency.setArtifactId(artifactId);
		dependency.setClassifier(classifier);
		dependency.setGroupId(groupId);
		dependency.setType(type);
		dependency.setVersion(version);

		return dependency;
	}

	protected abstract void doExecute() throws Exception;

	protected Artifact resolveArtifact(Dependency dependency) throws Exception {
		Artifact artifact = artifactFactory.createArtifact(
			dependency.getGroupId(), dependency.getArtifactId(),
			dependency.getVersion(), dependency.getClassifier(),
			dependency.getType());

		artifactResolver.resolve(
			artifact, remoteArtifactRepositories, localArtifactRepository);

		return artifact;
	}

	protected MavenProject resolveProject(Artifact artifact) throws Exception {
		Artifact pomArtifact = artifact;

		String type = artifact.getType();

		if (!type.equals("pom")) {
			pomArtifact = artifactFactory.createArtifact(
				artifact.getGroupId(), artifact.getArtifactId(),
				artifact.getVersion(), "", "pom");
		}

		return projectBuilder.buildFromRepository(
			pomArtifact, remoteArtifactRepositories, localArtifactRepository);
	}

	/**
	 * @parameter expression="${appServerPortalDir}"
	 */
	protected File appServerPortalDir;

	/**
	 * @parameter default-value="${appServerPortalDir}/WEB-INF/lib" expression="${appServerLibPortalDir}"
	 */
	protected File appServerLibPortalDir;

	/**
	 * @component
	 */
	protected ArchiverManager archiverManager;

	/**
	 * @component
	 */
	protected ArtifactFactory artifactFactory;

	/**
	 * @component
	 */
	protected ArtifactResolver artifactResolver;

	/**
	 * @parameter expression="${localRepository}"
	 * @readonly
	 * @required
	 */
	protected ArtifactRepository localArtifactRepository;

	/**
	 * @parameter default-value="portlet" expression="${pluginType}"
	 * @required
	 */
	protected String pluginType;

	/**
	 * @parameter expression="${project}"
	 * @required
	 * @readonly
	 */
	protected MavenProject project;

	/**
	 * @component role="org.apache.maven.project.MavenProjectBuilder"
	 * @required
	 * @readonly
	 */
	protected MavenProjectBuilder projectBuilder;

	/**
	 * @parameter expression="${project.remoteArtifactRepositories}"
	 * @readonly
	 * @required
	 */
	protected List remoteArtifactRepositories;

}