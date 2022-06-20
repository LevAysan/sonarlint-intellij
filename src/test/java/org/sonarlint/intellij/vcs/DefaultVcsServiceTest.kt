/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.intellij.vcs

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.testFramework.PsiTestUtil
import git4idea.GitVcs
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintHeavyTest
import org.sonarlint.intellij.common.vcs.VcsService
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.ProjectBinding
import org.sonarlint.intellij.messages.PROJECT_BINDING_TOPIC
import org.sonarlint.intellij.messages.PROJECT_SYNC_TOPIC
import org.sonarlint.intellij.util.ImmediateExecutorService
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBranches
import java.nio.file.Paths
import java.util.Optional
import kotlin.test.assertFailsWith

internal class DefaultVcsServiceTest : AbstractSonarLintHeavyTest() {

    private lateinit var connectedEngine : ConnectedSonarLintEngine
    private lateinit var vcsService: DefaultVcsService

    override fun setUp() {
        super.setUp()
        connectedEngine = mock(ConnectedSonarLintEngine::class.java)
        vcsService = DefaultVcsService(project, ImmediateExecutorService())
        replaceProjectService(VcsService::class.java, vcsService)
    }

    @Test
    fun test_should_not_resolve_server_branch_when_project_is_not_bound() {
        assertThat(vcsService.getServerBranchName(module)).isEqualTo("master")
    }

    @Test
    fun test_should_not_resolve_server_branch_when_project_is_not_a_git_repo() {
        connectProjectTo(ServerConnection.newBuilder().setName("connection").build(), "projectKey")
        connectModuleTo(module, "moduleKey")
        whenever(connectedEngine.getServerBranches("moduleKey")).thenReturn(
            ProjectBranches(
                setOf("master", "branch1"),
                "master"
            )
        )
        getEngineManager().registerEngine(connectedEngine, "connection")

        assertThat(vcsService.getServerBranchName(module)).isEqualTo("master")
    }

    @Test
    fun test_should_resolve_server_branch_from_first_repo_when_module_have_different_git_repo_as_content_roots() {
        val module = createModule("aModule")
        addContentRootWithGitRepo(module, "contentRoot1")
        addContentRootWithGitRepo(module, "contentRoot2")
        connectProjectTo(ServerConnection.newBuilder().setName("connection").build(), "projectKey")
        connectModuleTo(module, "moduleKey")
        whenever(connectedEngine.getServerBranches("moduleKey")).thenReturn(
            ProjectBranches(
                setOf("master", "branch1"),
                "master"
            )
        )
        getEngineManager().registerEngine(connectedEngine, "connection")

        val resolvedBranchName = vcsService.getServerBranchName(module)

        assertThat(resolvedBranchName).isEqualTo("master")
    }

    @Test
    fun test_should_resolve_exact_server_branch_when_module_is_bound_and_current_branch_is_known_on_server() {
        val module = createModule("aModule")
        addContentRootWithGitRepo(module)
        connectProjectTo(ServerConnection.newBuilder().setName("connection").build(), "projectKey")
        connectModuleTo(module, "moduleKey")
        whenever(connectedEngine.getServerBranches("moduleKey")).thenReturn(
            ProjectBranches(
                setOf("master", "branch1"),
                "master"
            )
        )
        getEngineManager().registerEngine(connectedEngine, "connection")

        val resolvedBranchName = vcsService.getServerBranchName(module)

        assertThat(resolvedBranchName).isEqualTo("branch1")
    }

    @Test
    fun test_should_return_cached_value_when_already_resolved() {
        val module = createModule("aModule")
        addContentRootWithGitRepo(module)
        connectProjectTo(ServerConnection.newBuilder().setName("connection").build(), "projectKey")
        connectModuleTo(module, "moduleKey")
        whenever(connectedEngine.getServerBranches("moduleKey")).thenReturn(
            ProjectBranches(
                setOf("master", "branch1"),
                "master"
            )
        )
        getEngineManager().registerEngine(connectedEngine, "connection")
        assertThat(vcsService.getServerBranchName(module)).isEqualTo("branch1")

        verify(connectedEngine, times(1)).getServerBranches(anyString())

        assertThat(vcsService.getServerBranchName(module)).isEqualTo("branch1")
    }

    @Test
    fun test_should_resolve_closest_server_branch() {
        val module = createModule("aModule")
        addContentRootWithGitRepo(module)
        connectProjectTo(ServerConnection.newBuilder().setName("connection").build(), "projectKey")
        connectModuleTo(module, "moduleKey")
        whenever(connectedEngine.getServerBranches("moduleKey")).thenReturn(
            ProjectBranches(
                setOf("master"),
                "master"
            )
        )
        getEngineManager().registerEngine(connectedEngine, "connection")

        // current branch is 'branch1'
        val resolvedBranchName = vcsService.getServerBranchName(module)

        assertThat(resolvedBranchName).isEqualTo("master")
    }

    @Test
    fun test_should_clear_cache_when_project_is_unbound() {
        val module = createModule("aModule")
        addContentRootWithGitRepo(module)
        connectProjectTo(ServerConnection.newBuilder().setName("connection").build(), "projectKey")
        whenever(connectedEngine.getServerBranches("projectKey")).thenReturn(
            ProjectBranches(
                setOf("master", "branch1"),
                "master"
            )
        )
        getEngineManager().registerEngine(connectedEngine, "connection")
        assertThat(vcsService.getServerBranchName(module)).isEqualTo("branch1")

        unbindProject()

        assertThat(vcsService.getServerBranchName(module)).isEqualTo("master")
    }

    @Test
    fun test_should_refresh_cache_when_project_is_bound() {
        val module = createModule("aModule")
        addContentRootWithGitRepo(module)
        whenever(connectedEngine.getServerBranches("projectKey")).thenReturn(
            ProjectBranches(
                setOf("master", "branch1"),
                "master"
            )
        )
        getEngineManager().registerEngine(connectedEngine, "connection")
        assertThat(vcsService.getServerBranchName(module)).isEqualTo("master")

        connectProjectTo(ServerConnection.newBuilder().setName("connection").build(), "projectKey")
        project.messageBus.syncPublisher(PROJECT_BINDING_TOPIC)
            .bindingChanged(null, ProjectBinding("connection", "projectKey", emptyMap()))

        assertThat(vcsService.getServerBranchName(module)).isEqualTo("branch1")
    }

    @Test
    fun test_should_refresh_cache_when_project_is_synchronized() {
        val module = createModule("aModule")
        addContentRootWithGitRepo(module)
        whenever(connectedEngine.getServerBranches("projectKey")).thenReturn(
            ProjectBranches(
                setOf("master"),
                "master"
            )
        )
        getEngineManager().registerEngine(connectedEngine, "connection")
        connectProjectTo(ServerConnection.newBuilder().setName("connection").build(), "projectKey")
        assertThat(vcsService.getServerBranchName(module)).isEqualTo("master")

        whenever(connectedEngine.getServerBranches("projectKey")).thenReturn(
            ProjectBranches(
                setOf("master", "branch1"),
                "master"
            )
        )
        project.messageBus.syncPublisher(PROJECT_SYNC_TOPIC)
            .synchronizationFinished()

        val resolvedBranchName = vcsService.getServerBranchName(module)

        assertThat(resolvedBranchName).isEqualTo("branch1")
    }

    private fun addContentRootWithGitRepo(module: Module, contentRootPath: String = "path") {
        val contentRootBasePath = Paths.get(ModuleUtil.getModuleDirPath(module)).resolve(contentRootPath).toFile()
        // this .git folder contains a repo with 2 branches: master and branch1 (current)
        FileUtil.copyDir(getTestDataPath().resolve("git/").toFile(), contentRootBasePath.resolve(".git/"))
        val rootVf = refreshAndFindFile(contentRootBasePath)
        val rootPath = rootVf.path
        PsiTestUtil.addContentRoot(module, rootVf)
        val vcsManager = ProjectLevelVcsManager.getInstance(project) as ProjectLevelVcsManagerImpl
        vcsManager.directoryMappings = vcsManager.directoryMappings + listOf(VcsDirectoryMapping(rootPath, GitVcs.NAME))
        vcsManager.waitForInitialized()
    }
}
