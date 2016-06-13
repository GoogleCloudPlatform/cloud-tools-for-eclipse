package com.google.cloud.tools.eclipse.usagetracker;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
  private static final String BUNDLE_NAME = "com.google.cloud.tools.eclipse.usagetracker.messages";
  public static String OPT_IN_NOTIFICATION_LINK;
  public static String OPT_IN_NOTIFICATION_TEXT;
  static {
    // initialize resource bundle
    NLS.initializeMessages(BUNDLE_NAME, Messages.class);
  }

  private Messages() {
  }
}
