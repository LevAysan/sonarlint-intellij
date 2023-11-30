/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
package org.sonarlint.intellij.ui.traffic.light

import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.GridBag
import org.apache.commons.lang.StringUtils
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.actions.ShowLogAction
import org.sonarlint.intellij.cayc.CleanAsYouCodeService
import org.sonarlint.intellij.util.HelpLabelUtils
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.finding.FindingType.ISSUE
import org.sonarlint.intellij.finding.FindingType.SECURITY_HOTSPOT
import org.sonarlint.intellij.finding.FindingType.TAINT_VULNERABILITY
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JPanel


class SonarLintDashboardPanel(private val editor: Editor) {

    companion object {
        private const val NO_FINDINGS_TEXT = "No problems found, keep up the good work!"
        private const val NO_CONNECTED_MODE_TITLE = "Connected mode is not active"
        private const val NO_BINDING_TITLE = "No binding found"
        private const val CHECKBOX_TITLE = "Focus on New Code"
    }

    val panel = JPanel(GridBagLayout())
    private val findingsSummaryLabel = JBLabel(NO_FINDINGS_TEXT)
    private val connectionIcon = JBLabel()
    private val connectionLabel = JBLabel(NO_CONNECTED_MODE_TITLE)
    private val connectionNameLabel = JBLabel()
    private val connectionHelp = HelpLabelUtils.createConnectedMode()
    private val bindingLabel = JBLabel(NO_BINDING_TITLE)
    private val focusOnNewCodeCheckbox = JBCheckBox(CHECKBOX_TITLE)

    init {
        editor.project?.let { refreshCheckbox(it) }
        focusOnNewCodeCheckbox.addActionListener {
            getService(CleanAsYouCodeService::class.java).setFocusOnNewCode(focusOnNewCodeCheckbox.isSelected)
        }
        focusOnNewCodeCheckbox.isOpaque = false

        val presentation = Presentation()
        presentation.icon = AllIcons.Actions.More
        presentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)

        val menuButton = ActionButton(
            MenuAction(),
            presentation,
            ActionPlaces.EDITOR_POPUP,
            ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
        )

        val gc =
            GridBag().nextLine().next().anchor(GridBagConstraints.LINE_START).weightx(1.0).fillCellHorizontally().insets(10, 10, 10, 10)

        panel.add(findingsSummaryLabel, gc)
        panel.add(menuButton, gc.next().anchor(GridBagConstraints.LINE_END).weightx(0.0).insets(10, 6, 10, 6))
        val connectedModePanel = JPanel(HorizontalLayout(5))
        connectedModePanel.add(connectionLabel)
        connectedModePanel.add(connectionIcon)
        connectedModePanel.add(connectionNameLabel)
        connectedModePanel.add(connectionHelp)
        panel.add(
            connectedModePanel,
            gc.nextLine().next().anchor(GridBagConstraints.LINE_START).fillCellHorizontally().coverLine().weightx(1.0).insets(0, 10, 10, 10)
        )
        panel.add(
            bindingLabel,
            gc.nextLine().next().anchor(GridBagConstraints.LINE_START).fillCellHorizontally().coverLine().weightx(1.0).insets(0, 10, 10, 10)
        )
        val focusPanel = JPanel(HorizontalLayout(5))
        focusPanel.add(focusOnNewCodeCheckbox)
        focusPanel.add(HelpLabelUtils.createCleanAsYouCode())
        panel.add(
            focusPanel,
            gc.nextLine().next().anchor(GridBagConstraints.LINE_START).fillCellHorizontally().coverLine().weightx(1.0).insets(0, 10, 10, 10)
        )
    }

    fun refresh(summary: SonarLintDashboardModel) {
        val project = editor.project ?: return
        refreshCheckbox(project)
        if (summary.findingsCount() == 0) {
            findingsSummaryLabel.text = NO_FINDINGS_TEXT
        } else {
            val fragments = mutableListOf<String>()
            with(summary) {
                if (issuesCount > 0) {
                    fragments.add(ISSUE.display(issuesCount))
                }
                if (hotspotsCount > 0) {
                    fragments.add(SECURITY_HOTSPOT.display(hotspotsCount))
                }
                if (taintVulnerabilitiesCount > 0) {
                    fragments.add(TAINT_VULNERABILITY.display(taintVulnerabilitiesCount))
                }
                findingsSummaryLabel.text = fragments.joinToString()
            }
        }

        val settings = Settings.getSettingsFor(project)
        settings.connectionName?.let { connectionName ->
            val serverConnection = getService(project, ProjectBindingManager::class.java).serverConnection

            connectionLabel.text = "Connected to:"
            connectionNameLabel.text = connectionName
            connectionNameLabel.isVisible = true
            connectionHelp.isVisible = false
            connectionIcon.isVisible = true
            if (serverConnection.isSonarCloud) {
                connectionIcon.icon = SonarLintIcons.ICON_SONARCLOUD_16
            } else {
                connectionIcon.icon = SonarLintIcons.ICON_SONARQUBE_16
            }
            settings.projectKey?.let { projectKey ->
                bindingLabel.isVisible = true
                bindingLabel.text = "Bound to project: ${StringUtils.abbreviate(projectKey, 100)}"
            } ?: run {
                bindingLabel.isVisible = false
                bindingLabel.text = NO_BINDING_TITLE
            }
        } ?: run {
            connectionLabel.text = NO_CONNECTED_MODE_TITLE
            connectionIcon.isVisible = false
            connectionNameLabel.isVisible = false
            connectionHelp.isVisible = true
            bindingLabel.isVisible = false
            bindingLabel.text = NO_BINDING_TITLE
        }
    }

    private fun refreshCheckbox(project: Project) {
        Settings.getSettingsFor(project).isBound.let {
            focusOnNewCodeCheckbox.isEnabled = it
            focusOnNewCodeCheckbox.text = if (it) CHECKBOX_TITLE else "$CHECKBOX_TITLE (connected mode only)"
        }
        val isFocusOnNewCode = Settings.getGlobalSettings().isFocusOnNewCode
        focusOnNewCodeCheckbox.isSelected = isFocusOnNewCode
    }

    private class MenuAction : DefaultActionGroup(), HintManagerImpl.ActionToIgnore {
        init {
            isPopup = true
            add(ActionManager.getInstance().getAction("SonarLint.toolwindow.Configure"))
            add(ShowLogAction())
        }
    }

}
