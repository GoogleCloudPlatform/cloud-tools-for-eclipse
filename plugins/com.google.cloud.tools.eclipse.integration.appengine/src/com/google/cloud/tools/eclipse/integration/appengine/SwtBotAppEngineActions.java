/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.eclipse.integration.appengine;

import com.google.cloud.tools.eclipse.swtbot.SwtBotTestingUtilities;
import com.google.cloud.tools.eclipse.swtbot.SwtBotTimeoutManager;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;

/**
 * Useful App Engine-related actions for Google Cloud Tools for Eclipse
 */
public class SwtBotAppEngineActions {

  /**
   * Create a native App Engine project using the
   * {@code com.google.cloud.tools.eclipse.appengine.newproject.AppEngineStandard} new wizard.
   * 
   * @param bot the bot
   * @param projectName the name of the project
   * @param location can be {@code null}
   * @param javaPackage can be {@code null} or empty
   * @param projectId can be {@code null}
   * @return the project
   */
  public static IProject createNativeWebAppProject(SWTWorkbenchBot bot, String projectName,
      String location, String javaPackage, String projectId) {
    bot.menu("File").menu("New").menu("Project...").click();

    SWTBotShell shell = bot.shell("New Project");
    shell.activate();

    bot.tree().expandNode("Google Cloud Platform")
        .select("Google App Engine Standard Java Project");
    bot.button("Next >").click();

    bot.textWithLabel("Project name:").setText(projectName);
    if (location == null) {
      bot.checkBox("Use default location").select();
    } else {
      bot.checkBox("Use default location").deselect();
      bot.textWithLabel("Location:").setText(location);
    }
    if (javaPackage != null) {
      bot.textWithLabel("Java package:").setText(javaPackage);
    }
    if (projectId != null) {
      bot.textWithLabel("App Engine Project ID: (optional)").setText(projectId);
    }
    SwtBotTestingUtilities.clickButtonAndWaitForWindowChange(bot, bot.button("Finish"));
    return getWorkspaceRoot().getProject(projectName);
  }

  /** Create a new project with the Maven-based Google App Engine Standard Java Project wizard */
  public static IProject createMavenWebAppProject(SWTWorkbenchBot bot, String location,
      String groupId, String artifactId, String javaPackage, String projectId,
      String archetypeDescription) {
    bot.menu("File").menu("New").menu("Project...").click();

    SWTBotShell shell = bot.shell("New Project");
    shell.activate();

    bot.tree().expandNode("Google Cloud Platform")
        .select("Maven-based Google App Engine Standard Java Project");
    bot.button("Next >").click();

    if (location == null) {
      bot.checkBox("Create project in workspace").select();
    } else {
      bot.checkBox("Create project in workspace").deselect();
      bot.textWithLabel("Location:").setText(location);
    }
    bot.textWithLabel("Group Id:").setText(groupId);
    bot.textWithLabel("Artifact Id:").setText(artifactId);
    if (javaPackage != null) {
      bot.textWithLabel("Java package:").setText(javaPackage);
    }
    if (projectId != null) {
      bot.textWithLabel("App Engine Project ID: (optional)").setText(projectId);
    }
    bot.button("Next >").click();
    // select an archetype; use the default
    if (archetypeDescription != null) {
      bot.list().select(archetypeDescription);
    }

    SwtBotTimeoutManager.setTimeout(15000); // can take a loooong time
    SwtBotTestingUtilities.clickButtonAndWaitForWindowChange(bot, bot.button("Finish"));
    SwtBotTimeoutManager.resetTimeout();
    // this isn't right for location != null
    return getWorkspaceRoot().getProject(artifactId);
  }

  private static IWorkspaceRoot getWorkspaceRoot() {
    return ResourcesPlugin.getWorkspace().getRoot();
  }

  private SwtBotAppEngineActions() {}
}
