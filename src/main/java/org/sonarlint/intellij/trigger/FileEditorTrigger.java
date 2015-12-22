/**
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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
package org.sonarlint.intellij.trigger;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.issue.IssueStore;

public class FileEditorTrigger extends AbstractProjectComponent implements FileEditorManagerListener {
  private final IssueStore store;
  private final PsiManager psiManager;

  public FileEditorTrigger(Project project, IssueStore store, PsiManager psiManager) {
    super(project);
    this.store = store;
    this.psiManager = psiManager;
    ApplicationManager.getApplication().getMessageBus().connect()
      .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this);
  }

  @Override
  public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    //nothing to do
  }

  @Override
  /**
   * Removes issues from the store that are located in the file that was closed.
   */
  public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    if (myProject.isDisposed()) {
      // don't risk do anything here, the store will be gone anyway
      return;
    }

    AccessToken token = ReadAction.start();
    try {
        store.clean(file);
    } finally {
      token.finish();
    }
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    // nothing to do
  }
}
