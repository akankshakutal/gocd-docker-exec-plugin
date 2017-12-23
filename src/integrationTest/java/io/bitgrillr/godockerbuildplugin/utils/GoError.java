package io.bitgrillr.godockerbuildplugin.utils;

/**
 * Exception indicating Go.cd returned a non 2XX response code.
 */
public class GoError extends Exception {

  /**
   * Create a new error with the given HTTP status code.
   *
   * @param statusCode Status code returned from Go.cd
   */
  public GoError(int statusCode) {
    super("HTTP: " + Integer.toString(statusCode));
  }

}