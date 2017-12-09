package io.bitgrillr.godockerbuildplugin.utils;

public class GoError extends Exception {

  public GoError(int statusCode) {
    super("HTTP: " + Integer.toString(statusCode));
  }

}