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
package org.sonarlint.intellij.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public class ProjectUtils {

  public static Collection<VirtualFile> getAllFiles(Project project) {
    var fileSet = new LinkedHashSet<VirtualFile>();
    iterateFilesToAnalyze(project, vFile -> {
      fileSet.add(vFile);
      // Continue collecting other files
      return true;
    });
    return fileSet;
  }

  public static boolean hasFiles(Project project) {
    var result = new AtomicBoolean(false);
    iterateFilesToAnalyze(project, vFile -> {
      result.set(true);
      // No need to iterate other files/folders
      return false;
    });
    return result.get();
  }

  private static void iterateFilesToAnalyze(Project project, Predicate<VirtualFile> fileProcessor) {
    var fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    fileIndex.iterateContent(vFile -> {
      if (!vFile.isDirectory() && !ProjectCoreUtil.isProjectOrWorkspaceFile(vFile)) {
        return fileProcessor.test(vFile);
      }
      // Continue iteration
      return true;
    });
  }

  private ProjectUtils() {
    // utility class
  }
}