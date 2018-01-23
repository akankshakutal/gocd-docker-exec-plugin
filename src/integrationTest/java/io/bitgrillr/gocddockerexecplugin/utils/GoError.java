/**
 * Copyright 2018 Christopher Arnold <cma.arnold@gmail.com> and contributors.
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
package io.bitgrillr.gocddockerexecplugin.utils;

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