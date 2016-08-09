package com.google.cloud.tools.eclipse.usagetracker;

public class AnalyticsEvents {

  // Google Analytics event actions
  public static final String LOGIN_START = "user.login.start";
  public static final String LOGIN_COMPLETE = "user.login.complete";

  public static final String APP_ENGINE_DEPLOY = "appengine.deploy";
  public static final String APP_ENGINE_DEPLOY_SUCCESS = "appengine.deploy.success";
  public static final String APP_ENGINE_LOCAL_SERVER = "appengine.local.dev.run";
  public static final String APP_ENGINE_NEW_PROJECT_WIZARD = "appengine.new.project.wizard";
  public static final String APP_ENGINE_NEW_PROJECT_WIZARD_COMPLETE =
      "appengine.new.project.wizard.complete";

  // Metadata keys
  public static final String APP_ENGINE_NEW_PROJECT_WIZARD_TYPE = "type";

  // Metadata values
  public static final String APP_ENGINE_NEW_PROJECT_WIZARD_TYPE_NATIVE = "native";
  public static final String APP_ENGINE_NEW_PROJECT_WIZARD_TYPE_MAVEN = "maven";
}